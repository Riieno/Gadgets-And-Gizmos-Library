package com.rieno.gadgetsandgizmos.lib.discovery;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResidencyReleaseTest{
    @Test void disabledOwnerReleasesRestoredTicketsWithoutAdoptingOrRemovingOtherOwners(){
        var level = mock(ServerLevel.class);
        var body = mock(ServerSubLevel.class);
        var container = mock(ServerSubLevelContainer.class);
        when(body.getLevel()).thenReturn(level);
        try(var access = mockStatic(SubLevelContainer.class)){
            access.when(() -> SubLevelContainer.getContainer(level)).thenReturn(container);
            SableSubLevelResidency.release("scm/one", List.of(body));
            verify(container, times(2)).removeForceLoadTicket(eq(body), any(SubLevelLoadingTicketType.class), eq("scm/one"));
            verifyNoMoreInteractions(container);
            SableSubLevelResidency.release("", List.of(body));
            SableSubLevelResidency.release("scm/two", null);
            verifyNoMoreInteractions(container);
        }
    }
}
