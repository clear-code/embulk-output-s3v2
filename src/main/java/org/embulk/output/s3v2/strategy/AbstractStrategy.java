package org.embulk.output.s3v2.strategy;

import org.embulk.output.s3v2.PluginTask;
import org.embulk.output.s3v2.s3.S3ClientManager;
import org.embulk.output.s3v2.s3.S3MultiPartStatus;
import org.embulk.spi.TransactionalFileOutput;

abstract class AbstractStrategy implements TransactionalFileOutput
{
    protected S3ClientManager s3;
    protected PluginTask task;
    protected int taskIndex;

    public AbstractStrategy(PluginTask task, int taskIndex)
    {
        this.task = task;
        this.taskIndex = taskIndex;

        // When validate() throws, this instance never reaches the caller, so Embulk
        // never gets a chance to call close(). Build the client only after validation.
        if (!validate()) {
            throw new IllegalArgumentException("Unsupported parameters combination");
        }

        this.s3 = new S3ClientManager(task.getEndpoint(), task.getRegion(), task.getEnableProfile(), task.getProfile());
    }

    /**
     * Validation for PluginTask parameters combination.
     * @see PluginTask
     */
    protected abstract boolean validate();

    protected final String getFileExtension()
    {
        return task.getExtension().startsWith(".") ? task.getExtension() : "." + task.getExtension();
    }

    protected final S3MultiPartStatus setUpS3MultiPartStatus()
    {
        return new S3MultiPartStatus(task.getMaxConcurrentRequests(), task.getMultipartChunksize(),
                task.getMultipartThreshold());
    }

    /**
     * Releases the S3 client held by this strategy.
     * close() is invoked by Embulk after commit() / abort(), so the client is
     * no longer used at this point. Subclasses overriding close() must call this.
     */
    @Override
    public void close()
    {
        s3.close();
    }
}
