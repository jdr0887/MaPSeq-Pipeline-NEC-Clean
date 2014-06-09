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
        String saHome = getWorkflowBeanService().getAttributes().get("sequenceAnalysisHome");

        File cohortDirectory = new File(saHome, "NEC");

        logger.info("cohortDirectory.getAbsolutePath(): {}", cohortDirectory.getAbsolutePath());

        WorkflowRun workflowRun = getWorkflowPlan().getWorkflowRun();

        for (HTSFSample htsfSample : htsfSampleSet) {

            if ("Undetermined".equals(htsfSample.getBarcode())) {
                continue;
            }

            logger.info("htsfSample: {}", htsfSample.toString());

            String subjectName = null;
            boolean qcPass = false;

            Set<EntityAttribute> attributeSet = workflowRun.getAttributes();
            if (attributeSet != null && !attributeSet.isEmpty()) {
                Iterator<EntityAttribute> attributeIter = attributeSet.iterator();
                while (attributeIter.hasNext()) {
                    EntityAttribute attribute = attributeIter.next();
                    String name = attribute.getName();
                    String value = attribute.getValue();
                    if ("subjectName".equals(name)) {
                        subjectName = value;
                    }
                    if ("qcPass".equals(name) && StringUtils.isNotEmpty(value) && "true".equalsIgnoreCase(value)) {
                        qcPass = true;
                    }
                }
            }
            if (StringUtils.isEmpty(subjectName)) {
                throw new WorkflowException("invalid subjectName");
            }

            SequencerRun sequencerRun = htsfSample.getSequencerRun();
            File outputDirectory = createOutputDirectory(sequencerRun.getName(), htsfSample,
                    getName().replace("Clean", ""), getVersion());
            logger.info("outputDirectory.getAbsolutePath() = {}", outputDirectory.getAbsolutePath());

            List<File> readPairList = WorkflowUtil.getReadPairList(htsfSample.getFileDatas(), sequencerRun.getName(),
                    htsfSample.getLaneIndex());

            // error check
            if (readPairList.size() != 2) {
                logger.warn("readPairList.size(): {}", readPairList.size());
                throw new WorkflowException("Read pair not found");
            }

            // fastq file names
            File r1FastqFile = readPairList.get(0);
            File r2FastqFile = readPairList.get(1);

            // project directories
            File subjectParentDirectory = new File(cohortDirectory, "subjects");
            File subjectDirectory = new File(subjectParentDirectory, subjectName);

            File sequenceProjDirectory = new File(subjectDirectory, "sequence");
            if (!sequenceProjDirectory.exists()) {
                sequenceProjDirectory.mkdirs();
            }

            File alignmentProjDirectory = new File(subjectDirectory, "alignment");
            File alignmentStatProjDirectory = new File(alignmentProjDirectory, "stats");
            if (!alignmentStatProjDirectory.exists()) {
                alignmentStatProjDirectory.mkdirs();
            }

            File qcFailProjDirectory = new File(subjectDirectory, "qc_fail");
            if (!qcFailProjDirectory.exists()) {
                qcFailProjDirectory.mkdirs();
            }

            File idchkProjDirectory = new File(cohortDirectory, "idchk");

            File idchkFVCFProjDirectory = new File(idchkProjDirectory, "fvcf");
            if (!idchkFVCFProjDirectory.exists()) {
                idchkFVCFProjDirectory.mkdirs();
            }

            File idchkECProjDirectory = new File(idchkProjDirectory, "ec");
            if (!idchkECProjDirectory.exists()) {
                idchkECProjDirectory.mkdirs();
            }

            try {

                List<File> deleteFileList = new ArrayList<File>();

                File sequencerRunOutputDirectory = new File(getOutputDirectory(), sequencerRun.getName());
                File analysisWorkflowDirectory = new File(sequencerRunOutputDirectory, "NEC/" + htsfSample.getName());

                String laneStr = String.format("L%03d", htsfSample.getLaneIndex());

                for (File f : analysisWorkflowDirectory.listFiles()) {
                    String fname = f.getName();
                    if (!fname.contains(laneStr)) {
                        deleteFileList.add(f);
                        continue;
                    }

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

                // don't link all files, just the analysis directory
                if (!qcPass) {

                    builder = WorkflowJobFactory.createJob(++count, SymlinkCLI.class, getWorkflowPlan(), htsfSample)
                            .siteName(siteName);
                    builder.addArgument(SymlinkCLI.TARGET, outputDirectory.getAbsolutePath())
                            .addArgument(SymlinkCLI.LINK, qcFailProjDirectory.getAbsolutePath())
                            .addArgument(SymlinkCLI.SLEEPDURATION, "3");
                    CondorJob job = builder.build();
                    logger.info(job.toString());
                    graph.addVertex(job);
                    continue;
                }

                String format = "%s,%s";
                List<String> targetLinkPairList = new ArrayList<String>();
                File fastqR1Symlink = new File(sequenceProjDirectory, r1FastqFile.getName());
                targetLinkPairList.add(String.format(format, r1FastqFile.getAbsolutePath(),
                        fastqR1Symlink.getAbsolutePath()));
                File fastqR2Symlink = new File(sequenceProjDirectory, r2FastqFile.getName());
                targetLinkPairList.add(String.format(format, r2FastqFile.getAbsolutePath(),
                        fastqR2Symlink.getAbsolutePath()));

                // cycle through all files in the analysisWorkflowDirectory
                for (File f : outputDirectory.listFiles()) {
                    String fname = f.getName();

                    if (fname.endsWith("fastqc.zip")) {

                        File targetFile = new File(sequenceProjDirectory, fname);
                        targetLinkPairList
                                .add(String.format(format, f.getAbsolutePath(), targetFile.getAbsolutePath()));

                    } else if (fname.endsWith("fixed-rg.bam") || fname.endsWith("fixed-rg.bai")) {

                        File targetFile = new File(alignmentProjDirectory, fname);
                        targetLinkPairList
                                .add(String.format(format, f.getAbsolutePath(), targetFile.getAbsolutePath()));

                    } else if (fname.contains(".coverage.") || fname.endsWith("flagstat")) {

                        File targetFile = new File(alignmentStatProjDirectory, fname);
                        targetLinkPairList
                                .add(String.format(format, f.getAbsolutePath(), targetFile.getAbsolutePath()));

                    } else if (fname.endsWith("fvcf")) {

                        File targetFile = new File(idchkFVCFProjDirectory, fname);
                        targetLinkPairList
                                .add(String.format(format, f.getAbsolutePath(), targetFile.getAbsolutePath()));

                    } else if (fname.endsWith("ec.tsv")) {

                        File targetFile = new File(idchkECProjDirectory, fname);
                        targetLinkPairList
                                .add(String.format(format, f.getAbsolutePath(), targetFile.getAbsolutePath()));

                    }

                }

                // new job
                builder = WorkflowJobFactory.createJob(++count, BatchSymlinkCLI.class, getWorkflowPlan(), htsfSample)
                        .siteName(siteName);
                for (String targetLinkPair : targetLinkPairList) {
                    builder.addArgument(BatchSymlinkCLI.TARGETLINKPAIR, targetLinkPair);
                }
                CondorJob symlinkJob = builder.build();
                logger.info(symlinkJob.toString());
                graph.addVertex(symlinkJob);

            } catch (Exception e) {
                throw new WorkflowException(e);
            }

        }

        return graph;
    }
}
