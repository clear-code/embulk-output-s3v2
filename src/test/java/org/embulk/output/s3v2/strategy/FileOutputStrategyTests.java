package org.embulk.output.s3v2.strategy;

import java.nio.file.Path;
import java.util.Optional;
import org.embulk.output.s3v2.PluginTask;
import org.embulk.spi.Buffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @see FileOutputStrategy
 */
@ExtendWith(MockitoExtension.class)
public class FileOutputStrategyTests
{
    @TempDir
    Path tempDir;

    private PluginTask task;

    @BeforeEach
    public void setUp()
    {
        task = Mockito.mock(PluginTask.class);
        Mockito.doReturn(Optional.empty()).when(task).getEndpoint();
        Mockito.doReturn("ap-northeast-1").when(task).getRegion();
        Mockito.doReturn(false).when(task).getEnableProfile();
        Mockito.doReturn("default").when(task).getProfile();
        Mockito.doReturn(".csv").when(task).getExtension();
        Mockito.doReturn("out").when(task).getObjectKeyPrefix();
        Mockito.doReturn(tempDir.toString()).when(task).getTempPath();
        Mockito.doReturn("embulk-output-s3v2").when(task).getTempFilePrefix();
    }

    private Buffer mockBuffer(int size)
    {
        Buffer buffer = Mockito.mock(Buffer.class);
        Mockito.doReturn(new byte[size]).when(buffer).array();
        Mockito.doReturn(0).when(buffer).offset();
        Mockito.doReturn(size).when(buffer).limit();
        return buffer;
    }

    /**
     * The caller hands the ownership of the buffer over to add(), so add() must release it.
     * Leaving it unreleased keeps the pooled buffer allocated for the lifetime of the
     * BufferAllocator, which EmbulkEmbed shares across every run.
     */
    @Test
    @DisplayName("add() releases the buffer it is given")
    public void testAddReleasesBuffer() throws Exception
    {
        FileOutputStrategy output = new FileOutputStrategy(task, 0);
        try {
            output.nextFile();
            Buffer buffer = mockBuffer(64);
            output.add(buffer);
            Mockito.verify(buffer).release();
        }
        finally {
            output.close();
        }
    }

    /**
     * The release must also happen when the write fails, hence the finally block.
     */
    @Test
    @DisplayName("add() releases the buffer even when the write fails")
    public void testAddReleasesBufferOnFailure() throws Exception
    {
        FileOutputStrategy output = new FileOutputStrategy(task, 0);
        output.nextFile();
        // Closing the stream first makes the next write fail. The payload must be larger than
        // BufferedOutputStream's internal buffer so that the write reaches the closed file.
        output.close();

        Buffer buffer = mockBuffer(64 * 1024);
        Assertions.assertThrows(RuntimeException.class, () -> output.add(buffer));
        Mockito.verify(buffer).release();
    }
}
