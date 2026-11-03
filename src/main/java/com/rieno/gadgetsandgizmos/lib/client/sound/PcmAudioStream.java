package com.rieno.gadgetsandgizmos.lib.client.sound;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.BooleanSupplier;

// Preserve PCM frames while seeking, converting positional audio and filling OpenAL buffers
public final class PcmAudioStream implements AudioStream{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final AudioStream source;
    private final AudioFormat format;
    private final ByteOrder inputOrder;
    private final int inputChannels;
    private final int inputFrame;
    private final int outputChannels;
    private ByteBuffer pending = ByteBuffer.allocate(0);
    private boolean ended;
    private boolean closed;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public PcmAudioStream(AudioStream source, boolean mono) throws IOException{
        this.source = java.util.Objects.requireNonNull(source);
        var src = source.getFormat();
        if(!AudioFormat.Encoding.PCM_SIGNED.equals(src.getEncoding()) || src.getSampleSizeInBits() != 16
                || src.getChannels() < 1 || src.getChannels() > 8 || !Float.isFinite(src.getSampleRate()) || src.getSampleRate() <= 0){
            throw new IOException("Unsupported music PCM format: " + src);
        }
        inputChannels = src.getChannels();
        inputFrame = inputChannels * 2;
        inputOrder = src.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        outputChannels = mono || inputChannels > 2 ? 1 : inputChannels;
        format = new AudioFormat(src.getSampleRate(), 16, outputChannels, true, ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);
    }

    @Override public AudioFormat getFormat(){ return format; }

    // Return full requested buffers until EOF, including the tail left after a seek
    @Override public synchronized ByteBuffer read(int size) throws IOException{
        if(closed) return null;
        int frame = outputChannels * 2;
        int bytes = Math.max(frame, size - Math.floorMod(size, frame));
        var res = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        while(res.remaining() >= frame && available(Math.max(inputFrame, res.remaining() / frame * inputFrame))){
            if(outputChannels == inputChannels && inputOrder == ByteOrder.nativeOrder()){
                int count = Math.min(res.remaining(), pending.remaining() / inputFrame * inputFrame);
                var chunk = pending.slice();
                chunk.limit(count);
                res.put(chunk);
                pending.position(pending.position() + count);
            }else if(outputChannels == 1){
                int sum = 0;
                for(int idx = 0; idx < inputChannels; idx++) sum += pending.getShort();
                res.putShort((short)(sum / inputChannels));
            }else{
                for(int idx = 0; idx < inputChannels; idx++) res.putShort(pending.getShort());
            }
        }
        return res.position() == 0 ? null : res.flip();
    }

    // Seek on a worker thread and retain decoder overread for the first playback buffer
    public void seekTicks(long ticks, BooleanSupplier cancelled) throws IOException{
        long frames = Math.min(Long.MAX_VALUE / format.getFrameSize(), (long)(Math.max(0, ticks) * (double)format.getSampleRate() / 20));
        long bytes = frames * format.getFrameSize();
        while(bytes > 0){
            if(cancelled.getAsBoolean()) throw new IOException("Music source stopped");
            var data = read((int)Math.min(65536, bytes));
            if(data == null) return;
            bytes -= data.remaining();
        }
    }

    private boolean available(int size) throws IOException{
        while(pending.remaining() < inputFrame && !ended){
            var next = source.read(size);
            if(next == null || !next.hasRemaining()){
                ended = true;
                if(pending.hasRemaining()) throw new IOException("Incomplete music PCM frame");
                break;
            }
            if(pending.hasRemaining()){
                var joined = ByteBuffer.allocateDirect(pending.remaining() + next.remaining());
                joined.put(pending).put(next).flip();
                pending = joined.order(inputOrder);
            }else pending = next.order(inputOrder);
        }
        return pending.remaining() >= inputFrame;
    }

    @Override public synchronized void close() throws IOException{
        if(closed) return;
        closed = true;
        pending = ByteBuffer.allocate(0);
        source.close();
    }
}
