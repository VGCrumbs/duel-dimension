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
