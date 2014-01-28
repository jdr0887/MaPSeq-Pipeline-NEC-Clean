package edu.unc.mapseq.executor.nec.clean;

import java.util.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NECCleanWorkflowExecutorService {

    private final Logger logger = LoggerFactory.getLogger(NECCleanWorkflowExecutorService.class);

    private final Timer mainTimer = new Timer();

    private NECCleanWorkflowExecutorTask task;

    private Long period = 5L;

    public NECCleanWorkflowExecutorService() {
        super();
    }

    public void start() throws Exception {
        logger.info("ENTERING start()");
        long delay = 1 * 60 * 1000;
        mainTimer.scheduleAtFixedRate(task, delay, period * 60 * 1000);
    }

    public void stop() throws Exception {
        logger.info("ENTERING stop()");
        mainTimer.purge();
        mainTimer.cancel();
    }

    public NECCleanWorkflowExecutorTask getTask() {
        return task;
    }

    public void setTask(NECCleanWorkflowExecutorTask task) {
        this.task = task;
    }

    public Long getPeriod() {
        return period;
    }

    public void setPeriod(Long period) {
        this.period = period;
    }

}
