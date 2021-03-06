package edu.unc.mapseq.workflow.nec.clean;

import java.io.File;
import java.util.ArrayList;
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

import edu.unc.mapseq.dao.model.Flowcell;
import edu.unc.mapseq.dao.model.Sample;
import edu.unc.mapseq.dao.model.WorkflowRunAttempt;
import edu.unc.mapseq.module.core.RemoveCLI;
import edu.unc.mapseq.workflow.WorkflowException;
import edu.unc.mapseq.workflow.impl.AbstractSampleWorkflow;
import edu.unc.mapseq.workflow.impl.WorkflowJobFactory;
import edu.unc.mapseq.workflow.impl.WorkflowUtil;

public class NECCleanWorkflow extends AbstractSampleWorkflow {

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

        Set<Sample> sampleSet = getAggregatedSamples();
        logger.info("sampleSet.size(): {}", sampleSet.size());

        WorkflowRunAttempt attempt = getWorkflowRunAttempt();

        String siteName = getWorkflowBeanService().getAttributes().get("siteName");

        for (Sample sample : sampleSet) {

            if ("Undetermined".equals(sample.getBarcode())) {
                continue;
            }

            logger.info(sample.toString());

            try {

                // Files to delete
                List<File> deleteFileList = new ArrayList<File>();

                // Get data associated with this sample, start with sequencer run
                Flowcell flowcell = sample.getFlowcell();

                // get fastq files
                List<File> readPairList = WorkflowUtil.getReadPairList(sample.getFileDatas(), flowcell.getName(),
                        sample.getLaneIndex());
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
                File necAlignmentDirectory = new File(sample.getOutputDirectory(), "NECAlignment");

                // find analysis files to delete
                String laneStr = String.format("L%03d", sample.getLaneIndex());

                // cycle through all files in the analysisWorkflowDirectory
                for (File f : necAlignmentDirectory.listFiles()) {
                    String fname = f.getName();

                    // continue only if file name contains correct lane index
                    if (!fname.contains(laneStr)) {
                        deleteFileList.add(f);
                        continue;
                    }

                    // skip files to save / link
                    if (fname.endsWith("fixed-rg.bam") || fname.endsWith("fixed-rg.bai")
                            || fname.endsWith("fastqc.zip")) {
                        continue;
                    }

                    deleteFileList.add(f);
                }

                File necVariantCallingDirectory = new File(sample.getOutputDirectory(), "NECVariantCalling");

                for (File f : necVariantCallingDirectory.listFiles()) {
                    String fname = f.getName();

                    // continue only if file name contains correct lane index
                    if (!fname.contains(laneStr)) {
                        deleteFileList.add(f);
                        continue;
                    }

                    // skip files to save / link
                    if (fname.endsWith("flagstat") || fname.contains(".coverage.")
                            || fname.endsWith(".realign.fix.pr.vcf")) {
                        continue;
                    }

                    deleteFileList.add(f);
                }

                File necICDirectory = new File(sample.getOutputDirectory(), "NECIDCheck");

                for (File f : necICDirectory.listFiles()) {
                    String fname = f.getName();

                    // continue only if file name contains correct lane index
                    if (!fname.contains(laneStr)) {
                        deleteFileList.add(f);
                        continue;
                    }

                    // skip files to save / link
                    if (fname.endsWith("ec.tsv") || fname.endsWith("fvcf")) {
                        continue;
                    }

                    deleteFileList.add(f);
                }

                CondorJobBuilder builder = WorkflowJobFactory.createJob(++count, RemoveCLI.class, attempt.getId())
                        .siteName(siteName);
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
