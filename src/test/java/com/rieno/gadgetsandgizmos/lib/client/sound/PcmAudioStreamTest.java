package com.rieno.gadgetsandgizmos.lib.client.sound;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.sounds.AudioStream;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PcmAudioStreamTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Test void handheldSamplesStayExactAcrossDecoderOverreadAndSplitFrames() throws IOException{
        short[] samples = waveform(1600);
        var source = new Source(samples, 2, ByteOrder.LITTLE_ENDIAN, 257);
        try(var stream = new PcmAudioStream(source, false)){
            assertEquals(2, stream.getFormat().getChannels());
            var first = stream.read(1000);
            assertEquals(1000, first.remaining());
            assertArrayEquals(Arrays.copyOf(samples, 500), shorts(first));
            assertArrayEquals(Arrays.copyOfRange(samples, 500, 1000), shorts(stream.read(1000)));
            assertArrayEquals(Arrays.copyOfRange(samples, 1000, 1600), shorts(stream.read(2000)));
            assertNull(stream.read(1000));
            assertNull(stream.read(1000));
        }
        assertEquals(1, source.closes);
    }

    @Test void placedStereoIsAveragedWithoutSwappingBytesOrDroppingSamples() throws IOException{
        short[] samples = waveform(1600);
        try(var stream = new PcmAudioStream(new Source(samples, 2, ByteOrder.LITTLE_ENDIAN, 257), true)){
            assertEquals(1, stream.getFormat().getChannels());
            var mono = shorts(stream.read(1600));
            assertEquals(800, mono.length);
            for(int idx = 0; idx < mono.length; idx++) assertEquals((short)((samples[idx * 2] + samples[idx * 2 + 1]) / 2), mono[idx]);
            assertNull(stream.read(200));
        }
    }

    @Test void seeksPreserveTheDecoderTailAndFillTheFirstOpenAlBuffer() throws IOException{
        short[] samples = waveform(4000);
        try(var stream = new PcmAudioStream(new Source(samples, 2, ByteOrder.LITTLE_ENDIAN, 997), false)){
            stream.seekTicks(1, () -> false);
            var data = stream.read(1000);
            assertEquals(1000, data.remaining());
            assertArrayEquals(Arrays.copyOfRange(samples, 100, 600), shorts(data));
        }
    }

    @Test void monoSeekUsesOutputFramesAndConvertsBigEndianInput() throws IOException{
        short[] samples = waveform(4000);
        try(var stream = new PcmAudioStream(new Source(samples, 2, ByteOrder.BIG_ENDIAN, 257), true)){
            stream.seekTicks(1, () -> false);
            var data = shorts(stream.read(200));
            assertEquals(100, data.length);
            for(int idx = 0; idx < data.length; idx++) assertEquals((short)((samples[100 + idx * 2] + samples[101 + idx * 2]) / 2), data[idx]);
        }
    }

    @Test void closingIsIdempotentAndCancellationStopsASeek() throws IOException{
        var source = new Source(waveform(1000), 1, ByteOrder.LITTLE_ENDIAN, 100);
        var stream = new PcmAudioStream(source, false);
        assertThrows(IOException.class, () -> stream.seekTicks(10, () -> true));
        stream.close(); stream.close();
        assertNull(stream.read(20));
        assertEquals(1, source.closes);
    }

    @Test void partialFinalFramesFailInsteadOfProducingCorruptAudio() throws IOException{
        var source = new Source(new short[]{1, 2, 3}, 2, ByteOrder.LITTLE_ENDIAN, 6);
        try(var stream = new PcmAudioStream(source, false)){
            assertThrows(IOException.class, () -> stream.read(100));
        }
    }

    @Test void oversizedSeeksReachEofWithoutOverflowingTheByteCount() throws IOException{
        try(var stream = new PcmAudioStream(new Source(waveform(1000), 2, ByteOrder.LITTLE_ENDIAN, 257), false)){
            stream.seekTicks(Long.MAX_VALUE, () -> false);
            assertNull(stream.read(100));
        }
    }

    private static short[] waveform(int count){
        var res = new short[count];
        for(int idx = 0; idx < count; idx++) res[idx] = (short)(Math.sin(idx * .17) * 32000);
        return res;
    }

    private static short[] shorts(ByteBuffer data){
        data.order(ByteOrder.nativeOrder());
        var res = new short[data.remaining() / 2];
        for(int idx = 0; idx < res.length; idx++) res[idx] = data.getShort();
        return res;
    }

    private static final class Source implements AudioStream{
        private final byte[] data;
        private final AudioFormat format;
        private final int chunk;
        private int pos;
        private int closes;

        private Source(short[] samples, int channels, ByteOrder order, int chunk){
            var buffer = ByteBuffer.allocate(samples.length * 2).order(order);
            for(short sample : samples) buffer.putShort(sample);
            data = buffer.array();
            this.chunk = chunk;
            format = new AudioFormat(1000, 16, channels, true, order == ByteOrder.BIG_ENDIAN);
        }

        @Override public AudioFormat getFormat(){ return format; }
        @Override public ByteBuffer read(int ignored){
            int count = Math.min(chunk, data.length - pos);
            var res = ByteBuffer.allocateDirect(count).put(data, pos, count).flip();
            pos += count;
            return res;
        }
        @Override public void close(){ closes++; }
    }
}
