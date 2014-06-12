package edu.unc.mapseq.workflow.nec.clean;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.renci.jlrm.condor.CondorJob;
import org.renci.jlrm.condor.CondorJobBuilder;
import org.renci.jlrm.condor.CondorJobEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.unc.mapseq.dao.model.EntityAttribute;
import edu.unc.mapseq.dao.model.HTSFSample;
import edu.unc.mapseq.dao.model.SequencerRun;
import edu.unc.mapseq.dao.model.WorkflowRun;
import edu.unc.mapseq.module.core.BatchSymlinkCLI;
import edu.unc.mapseq.module.core.RemoveCLI;
import edu.unc.mapseq.module.core.SymlinkCLI;
import edu.unc.mapseq.workflow.WorkflowException;
import edu.unc.mapseq.workflow.WorkflowUtil;
import edu.unc.mapseq.workflow.impl.AbstractWorkflow;
import edu.unc.mapseq.workflow.impl.WorkflowJobFactory;

public class NECCleanWorkflow extends AbstractWorkflow {

    private final Logger logger = LoggerFactory.getLogger(NECCleanWorkflow.class);

    public NECCleanWorkflow() {
        super();
    }

    @Override
    public String getName() {
        return NECCleanWorkflow.class.getSimpleName().replace("Workflow", "");
    }

    @Override
    public String getVersion() {
        ResourceBundle bundle = ResourceBundle.getBundle("edu/unc/mapseq/workflow/nec/clean/workflow");
        String version = bundle.getString("version");
        return StringUtils.isNotEmpty(version) ? version : "0.0.1-SNAPSHOT";
    }

    @Override
    public Graph<CondorJob, CondorJobEdge> createGraph() throws WorkflowException {
        logger.info("ENTERING createGraph()");

        DirectedGraph<CondorJob, CondorJobEdge> graph = new DefaultDirectedGraph<CondorJob, CondorJobEdge>(
                CondorJobEdge.class);

        int count = 0;

        Set<HTSFSample> htsfSampleSet = getAggregateHTSFSampleSet();
        logger.info("htsfSampleSet.size(): {}", htsfSampleSet.size());

        String siteName = getWorkflowBeanService().getAttributes().get("siteName");

        for (HTSFSample htsfSample : htsfSampleSet) {

            if ("Undetermined".equals(htsfSample.getBarcode())) {
                continue;
            }

            logger.info("htsfSample: {}", htsfSample.toString());

            try {

                // Files to delete
                List<File> deleteFileList = new ArrayList<File>();

                // Get data associated with this htsf sample, start with sequencer run
                SequencerRun sequencerRun = htsfSample.getSequencerRun();
                logger.debug("sequencerRun: {}", sequencerRun.toString());

                // get fastq files
                List<File> readPairList = WorkflowUtil.getReadPairList(htsfSample.getFileDatas(),
                        sequencerRun.getName(), htsfSample.getLaneIndex());
                logger.debug("readPairList.size(): {}", readPairList.size());

                // error check
                if (readPairList.size() != 2) {
                    logger.warn("readPairList.size(): {}", readPairList.size());
                    throw new WorkflowException("Read pair not found");
                }

                // fastq file names
                // File r1FastqFile = readPairList.get(0);
                // File r2FastqFile = readPairList.get(1);

                // directories
                File sequencerRunOutputDirectory = new File(getOutputDirectory(), sequencerRun.getName());
                // File casavaWorkflowDirectory = new File(sequencerRunOutputDirectory, "CASAVA/" +
                // htsfSample.getName());
                //
                // // cycle through all files in the casavaWorkflowDirectory
                // for (File f : casavaWorkflowDirectory.listFiles()) {
                // if (!f.getAbsolutePath().equals(r1FastqFile.getAbsolutePath())
                // && !f.getAbsolutePath().equals(r2FastqFile.getAbsolutePath())) {
                // deleteFileList.add(f);
                // }
                // }

                File projectDirectory = new File(sequencerRunOutputDirectory, "NEC");

                File analysisWorkflowDirectory = new File(projectDirectory, htsfSample.getName());

                // find analysis files to delete
                String laneStr = String.format("L%03d", htsfSample.getLaneIndex());

                // cycle through all files in the analysisWorkflowDirectory
                for (File f : analysisWorkflowDirectory.listFiles()) {
                    String fname = f.getName();

                    // continue only if file name contains correct lane index
                    if (!fname.contains(laneStr)) {
                        deleteFileList.add(f);
                        continue;
                    }

                    // skip files to save / link
                    if (fname.endsWith("fixed-rg.bam") || fname.endsWith("fixed-rg.bai") || fname.endsWith("flagstat")
                            || fname.endsWith("fastqc.zip") || fname.endsWith("fvcf") || fname.endsWith("ec.tsv")
                            || fname.contains(".coverage.")) {
                        continue;
                    }

                    deleteFileList.add(f);
                }

                CondorJobBuilder builder = WorkflowJobFactory.createJob(++count, RemoveCLI.class, getWorkflowPlan(),
                        htsfSample, false).siteName(siteName);
                for (File file : deleteFileList) {
                    builder.addArgument(RemoveCLI.FILE, file.getAbsolutePath());
                }
                CondorJob removeJob = builder.build();
                logger.info(removeJob.toString());
                graph.addVertex(removeJob);

            } catch (Exception e) {
                throw new WorkflowException(e);
            }

        }

        return graph;
    }
}
