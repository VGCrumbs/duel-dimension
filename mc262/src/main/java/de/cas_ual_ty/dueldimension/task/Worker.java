package de.cas_ual_ty.dueldimension.task;

import de.cas_ual_ty.dueldimension.DuelDimension;

import java.util.concurrent.TimeUnit;

public class Worker extends Thread
{
    public final int index;
    public final long sleepMillis;
    public volatile boolean isWorking;
    
    public Worker(String name, int index, long sleepMillis)
    {
        super(name);
        this.index = index;
        this.sleepMillis = sleepMillis;
        isWorking = false;

        // Daemon, or the client cannot shut down at all.
        //
        // These four were the only non-daemon threads the mod starts, and a JVM
        // does not begin its exit sequence until every non-daemon thread has
        // ended. The loop below only ends once forceTaskStop is set, and the one
        // thing that sets it is WorkerManager's shutdown hook -- which by
        // definition cannot run until that exit sequence has already begun. So
        // main returns, these four hold the JVM open, the hook never fires, and
        // Minecraft's shutdown watchdog eventually halts the process.
        //
        // halt() skips shutdown hooks, which means the hook's actual job --
        // flushing the queue's outstanding BINDER_SAVE work -- was being dropped
        // every time the client was closed. Marking them daemon breaks the
        // cycle: the JVM exits when main does, hooks run properly, and the hook
        // still joins these threads so a task in flight is not cut off midway.
        setDaemon(true);
    }
    
    @Override
    public void run()
    {
        Task t;
        
        while(DuelDimension.proxy.continueTasks() && !DuelDimension.proxy.forceTaskStop())
        {
            t = TaskQueue.pollTask();
            
            if(t != null)
            {
                if(!isWorking)
                {
                    isWorking = true;
                }
                
                try
                {
                    t.run();
                }
                catch(Exception e)
                {
                    DuelDimension.log("Task failed!");
                    e.printStackTrace();
                }
            }
            else
            {
                if(isWorking)
                {
                    isWorking = false;
                }
                
                try
                {
                    TimeUnit.MILLISECONDS.sleep(sleepMillis);
                }
                catch(InterruptedException e)
                {
                    DuelDimension.log("Worker failed!");
                    e.printStackTrace();
                    WorkerManager.failedCallback(index);
                    return;
                }
            }
        }
    }
}
