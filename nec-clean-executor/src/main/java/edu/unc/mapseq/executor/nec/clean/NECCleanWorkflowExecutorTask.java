package edu.unc.mapseq.executor.nec.clean;

import java.util.Date;
import java.util.List;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.unc.mapseq.dao.MaPSeqDAOException;
import edu.unc.mapseq.dao.WorkflowDAO;
import edu.unc.mapseq.dao.WorkflowRunAttemptDAO;
import edu.unc.mapseq.dao.model.Workflow;
import edu.unc.mapseq.dao.model.WorkflowRunAttempt;
import edu.unc.mapseq.workflow.WorkflowBeanService;
import edu.unc.mapseq.workflow.WorkflowExecutor;
import edu.unc.mapseq.workflow.WorkflowTPE;
import edu.unc.mapseq.workflow.nec.clean.NECCleanWorkflow;

public class NECCleanWorkflowExecutorTask extends TimerTask {

    private final Logger logger = LoggerFactory.getLogger(NECCleanWorkflowExecutorTask.class);

    private final WorkflowTPE threadPoolExecutor = new WorkflowTPE();

    private WorkflowBeanService workflowBeanService;

    public NECCleanWorkflowExecutorTask() {
        super();
    }

    @Override
    public void run() {
        logger.info("ENTERING run()");

        threadPoolExecutor.setCorePoolSize(workflowBeanService.getCorePoolSize());
        threadPoolExecutor.setMaximumPoolSize(workflowBeanService.getMaxPoolSize());

        logger.info(String.format("ActiveCount: %d, TaskCount: %d, CompletedTaskCount: %d",
                threadPoolExecutor.getActiveCount(), threadPoolExecutor.getTaskCount(),
                threadPoolExecutor.getCompletedTaskCount()));

        WorkflowDAO workflowDAO = this.workflowBeanService.getMaPSeqDAOBean().getWorkflowDAO();
        WorkflowRunAttemptDAO workflowRunAttemptDAO = this.workflowBeanService.getMaPSeqDAOBean()
                .getWorkflowRunAttemptDAO();

        try {
            List<Workflow> workflowList = workflowDAO.findByName("NECClean");
            if (workflowList == null || (workflowList != null && workflowList.isEmpty())) {
                logger.error("No Workflow Found: {}", "NECClean");
                return;
            }
            Workflow workflow = workflowList.get(0);
            List<WorkflowRunAttempt> attempts = workflowRunAttemptDAO.findEnqueued(workflow.getId());
            if (attempts != null && !attempts.isEmpty()) {
                logger.info("dequeuing {} WorkflowRunAttempt", attempts.size());
                for (WorkflowRunAttempt attempt : attempts) {

                    NECCleanWorkflow nidaUCSFCleanWorkflow = new NECCleanWorkflow();
                    attempt.setVersion(nidaUCSFCleanWorkflow.getVersion());
                    attempt.setDequeued(new Date());
                    workflowRunAttemptDAO.save(attempt);

                    nidaUCSFCleanWorkflow.setWorkflowBeanService(workflowBeanService);
                    nidaUCSFCleanWorkflow.setWorkflowRunAttempt(attempt);
                    threadPoolExecutor.submit(new WorkflowExecutor(nidaUCSFCleanWorkflow));

                }

            }

        } catch (MaPSeqDAOException e) {
            e.printStackTrace();
        }

    }

    public WorkflowBeanService getWorkflowBeanService() {
        return workflowBeanService;
    }

    public void setWorkflowBeanService(WorkflowBeanService workflowBeanService) {
        this.workflowBeanService = workflowBeanService;
    }

}
