/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Red Hat, Inc., Stephen Connolly, id:cactusman, Yahoo!, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tasks.test;

import hudson.Extension;
import hudson.Functions;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Build;
import hudson.model.HealthReport;
import hudson.model.HealthReportingAction;
import hudson.model.Project;
import hudson.model.ResultTrend;
import hudson.model.Run;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.util.Area;
import hudson.util.ChartUtil;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;
import jenkins.model.RunAction2;
import jenkins.model.lazy.LazyBuildMixIn;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.urls.XYURLGenerator;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleInsets;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common base class for recording test result.
 *
 * <p>
 * {@link Project} and {@link Build} recognizes {@link Action}s that derive from this,
 * and displays it nicely (regardless of the underlying implementation.)
 *
 * @author Kohsuke Kawaguchi
 */

@ExportedBean
public abstract class AbstractTestResultAction<T extends AbstractTestResultAction> implements HealthReportingAction, RunAction2 {

    private static final Logger LOGGER = Logger.getLogger(AbstractTestResultAction.class.getName());
    /**
     * @since 1.2-beta-1
     */
    public transient Run<?, ?> run;
    @Deprecated
    public transient AbstractBuild<?, ?> owner;

    private Map<String, String> descriptions = new ConcurrentHashMap<>();

    /**
     * Tool tips for "Overall Build Analysis" trend type.
     */
    private Map<ChartUtil.NumberOnlyBuildLabel, String> failToolTip, totalToolTip, skipToolTip;

    /**
     * Tool tips for "Lengthy Tests" trend type.
     */
    private Map<ChartUtil.NumberOnlyBuildLabel, String> lengthyToolTip;

    /**
     * Array for storing package hierarchy derived from each testcase, used for selecting a particular
     * project.
     */
    private String[] projectList;

    private List<String> mostFailedTestCases;

    private Map<Integer, ArrayList<Integer>> flapperInfo;
    private Map<Integer, Integer> flapperCountToolTip;

    /**
     * @since 1.545
     */
    protected AbstractTestResultAction() {
    }

    /**
     * @since 1.2-beta-1
     * @deprecated Use the default constructor and just call {@link Run#addAction} to associate the build with the action.
     */
    @Deprecated
    protected AbstractTestResultAction(Run owner) {
        onAttached(owner);
    }

    @Deprecated
    protected AbstractTestResultAction(AbstractBuild owner) {
        this((Run) owner);
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
        this.owner = r instanceof AbstractBuild ? (AbstractBuild<?, ?>) r : null;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
        this.owner = r instanceof AbstractBuild ? (AbstractBuild<?, ?>) r : null;
    }

    /**
     * Gets the number of failed tests.
     */
    @Exported(visibility = 2)
    public abstract int getFailCount();

    /**
     * Gets the number of skipped tests.
     */
    @Exported(visibility = 2)
    public int getSkipCount() {
        // Not all sub-classes will understand the concept of skipped tests.
        // This default implementation is for them, so that they don't have
        // to implement it (this avoids breaking existing plug-ins - i.e. those
        // written before this method was added in 1.178).
        // Sub-classes that do support skipped tests should over-ride this method.
        return 0;
    }

    /**
     * Gets the total number of tests.
     */
    @Exported(visibility = 2)
    public abstract int getTotalCount();

    /**
     * Gets the diff string of failures.
     */
    public final String getFailureDiffString() {
        T prev = getPreviousResult();
        if (prev == null)
            return "";  // no record

        return " / " + Functions.getDiffString(this.getFailCount() - prev.getFailCount());
    }

    public String getDisplayName() {
        return Messages.AbstractTestResultAction_getDisplayName();
    }

    @Exported(visibility = 2)
    public String getUrlName() {
        return "testReport";
    }

    public String getIconFileName() {
        return "clipboard.png";
    }

    public HealthReport getBuildHealth() {
        final double scaleFactor = getHealthScaleFactor();
        if (scaleFactor < 1e-7) {
            return null;
        }
        final int totalCount = getTotalCount();
        final int failCount = getFailCount();
        int score = (totalCount == 0)
                ? 100
                : (int) (100.0 * Math.max(0.0, Math.min(1.0, 1.0 - (scaleFactor * failCount) / totalCount)));
        Localizable description;
        Localizable displayName = Messages._AbstractTestResultAction_getDisplayName();
        if (totalCount == 0) {
            description = Messages._AbstractTestResultAction_zeroTestDescription(displayName);
        }
        else {
            description = Messages._AbstractTestResultAction_TestsDescription(displayName, failCount, totalCount);
        }
        return new HealthReport(score, description);
    }

    /**
     * Returns how much to scale the test related health by.
     *
     * @return a factor of {@code 1.0} to have the test health be the percentage of tests passing so 20% of tests
     * failing will report as 80% health. A factor of {@code 2.0} will mean that 20% of tests failing will report as 60%
     * health. A factor of {@code 2.5} will mean that 20% of test failing will report as 50% health. A factor of
     * {@code 4.0} will mean that 20% of tests failing will report as 20% health. A factor of {@code 5.0} will mean
     * that 20% (or more) of tests failing will report as 0% health. A factor of {@code 0.0} will disable test health
     * reporting.
     */
    public double getHealthScaleFactor() {
        return 1.0;
    }

    /**
     * Exposes this object to the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Returns the object that represents the actual test result.
     * This method is used by the remote API so that the XML/JSON
     * that we are sending won't contain unnecessary indirection
     * (that is, {@link AbstractTestResultAction} in between.
     *
     * <p>
     * If such a concept doesn't make sense for a particular subtype,
     * return <code>this</code>.
     */
    public abstract Object getResult();

    /**
     * Gets the test result of the previous build, if it's recorded, or null.
     */
    public T getPreviousResult() {
        return (T) getPreviousResult(getClass(), true);
    }

    private <U extends AbstractTestResultAction> U getPreviousResult(Class<U> type, boolean eager) {
        Run<?, ?> b = run;
        Set<Integer> loadedBuilds;
        if (!eager && run.getParent() instanceof LazyBuildMixIn.LazyLoadingJob) {
            loadedBuilds = ((LazyBuildMixIn.LazyLoadingJob<?, ?>) run.getParent()).getLazyBuildMixIn()._getRuns().getLoadedBuilds().keySet();
        }
        else {
            loadedBuilds = null;
        }
        while (true) {
            b = loadedBuilds == null || loadedBuilds.contains(b.number - /* assuming there are no gaps */1) ? b.getPreviousBuild() : null;
            if (b == null)
                return null;
            U r = b.getAction(type);
            if (r != null) {
                if (r == this) {
                    throw new IllegalStateException(this + " was attached to both " + b + " and " + run);
                }
                if (r.run.number != b.number) {
                    throw new IllegalStateException(r + " was attached to both " + b + " and " + r.run);
                }
                return r;
            }
        }
    }

    public TestResult findPreviousCorresponding(TestResult test) {
        T previousResult = getPreviousResult();
        if (previousResult != null) {
            TestResult testResult = (TestResult) getResult();
            return testResult.findCorrespondingResult(test.getId());
        }

        return null;
    }

    public TestResult findCorrespondingResult(String id) {
        final Object testResult = getResult();
        if (!(testResult instanceof TestResult)) {
            return null;
        }
        return ((TestResult) testResult).findCorrespondingResult(id);
    }

    /**
     * A shortcut for summary.jelly
     *
     * @return List of failed tests from associated test result.
     */
    public List<? extends TestResult> getFailedTests() {
        return Collections.emptyList();
    }

    /**
     * A shortcut for scripting
     *
     * @return List of passed tests from associated test result.
     * @since 1.10
     */
    @Nonnull
    public List<? extends TestResult> getPassedTests() {
        return Collections.emptyList();
    }

    /**
     * A shortcut for scripting
     *
     * @return List of skipped tests from associated test result.
     * @since 1.10
     */
    @Nonnull
    public List<? extends TestResult> getSkippedTests() {
        return Collections.emptyList();
    }

    /**
     * Start for Test Result Trends.
     * From here onwards start the code area responsible for generating various test result trends.
     */

    public hudson.tasks.junit.TestResult loadXml() {
        return null;
    }

    /**
     * A method for getting the list of packages for all levels of hierarchy.
     *
     * @return Array of packages of all hierarchies.
     */

    public final String[] getProjectList() {

        /*
         * If project list is already ready no need to construct again.
         */
        if (null != projectList)
            return projectList;
        hudson.tasks.junit.TestResult r = loadXml();
        Collection<SuiteResult> suiteList = r.getSuites();

        /*
         * A set for the package names.
         */
        Map<Integer, HashSet<String>> projectSet = new HashMap<>();
        int projectCount = 0;
        final int projectCountLimit = 50;
        int levelCap = Integer.MAX_VALUE;
        for (SuiteResult suiteResult : suiteList) {
            String suiteName = suiteResult.getName();
            String projectName = "";

            String[] packageTree = suiteName.split("[.]");

            /*
             * Iterating till the length-1 of packageTree array to exclude class name.
             */
            for (int level = 0; level < packageTree.length - 1 && level <= levelCap; level++) {
                if (!projectName.isEmpty())
                    projectName += '.';
                projectName += packageTree[level];
                if (!projectSet.containsKey(level)) {
                    projectSet.put(level, new HashSet<String>());
                }
                if (!projectSet.get(level).contains(projectName)) {
                    projectSet.get(level).add(projectName);
                    projectCount++;
                }
                if (projectCount > projectCountLimit) {
                    int levelToDel = projectSet.size() - 1;
                    int deletionSize = projectSet.get(levelToDel).size();
                    projectSet.remove(levelToDel);
                    projectCount -= deletionSize;
                    levelCap = projectSet.size() - 1;
                }
            }
        }

        /*
         * Converting the set of package names to the corresponding array
         * and finally sorting it in ascending order.
         */
        projectList = new String[projectCount];
        int index = 0;
        for (int level : projectSet.keySet()) {
            Set<String> projectSetPerLevel = projectSet.get(level);
            for (String projectName : projectSetPerLevel) {
                projectList[index] = projectName;
                index++;
            }
        }
        Arrays.sort(projectList);
        return projectList;
    }

    /**
     * Generates a PNG image for the test result trend.
     */
    public void doGraph(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (ChartUtil.awtProblemCause != null) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath() + "/images/headless.png");
            return;
        }

        if (req.checkIfModified(run.getTimestamp(), rsp))
            return;

        /*
         * Utility method for creating various test result trends.
         */
        doGraphUtil(req, rsp);
    }

    /**
     * A utility method for constructing trends based upon the query parameters passed in the
     * request message.
     *
     * @param req HTTP request message for the image of trend.
     * @param rsp HTTP response message for the requested image.
     * @throws IOException in case an exception occurs in
     *                     {@link ChartUtil#generateGraph(StaplerRequest, StaplerResponse, JFreeChart, Area)}
     */
    public void doGraphUtil(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String projectLevel = getParameter(req, AbstractTestResultAction.PROJECTLEVEL);
        String trendType = getParameter(req, AbstractTestResultAction.TRENDTYPE);

        /*
         * A binary search for verifying whether the given project level is valid or not. If found in the
         * array or is equal to "AllProjects" the valid else not.
         */
        int index = Arrays.binarySearch(projectList, projectLevel);
        if ((index >= 0 || projectLevel.equals(AbstractTestResultAction.ALLPROJECTS)) && trendType.equals(AbstractTestResultAction.BUILD_ANALYSIS)) {

            /*
             * This method generates the trend depicting no. of failed, passed and skipped testcases for
             * the specified project or for all projects.
             */
            //ChartUtil.generateGraph(req, rsp, createChart(req, buildDataSet(req)), calcDefaultSize());
            ChartUtil.generateGraph(req, rsp, createChart(req, buildDataSetPerProject(req)), calcDefaultSize());
        }
        else if ((index >= 0 || projectLevel.equals(AbstractTestResultAction.ALLPROJECTS)) && trendType.equals(AbstractTestResultAction.LENGTHY_TESTS)) {

            /*
             * This method generates the trends depicting no. of passed testcases which took longer duration
             * to run in the given build.
             */
            ChartUtil.generateGraph(req, rsp, createChart(req, buildLengthyTestDataset(req)), calcDefaultSize());
        }
        else if ((index >= 0 || projectLevel.equals(AbstractTestResultAction.ALLPROJECTS)) && trendType.equals(AbstractTestResultAction.FLAKY_TESTS)) {

            /*
             * This method generates the trends depicting no. of passed and failed testcases which were
             * inconsistently failing or passing i.e. flappy behaviour.
             */
            ChartUtil.generateGraph(req, rsp, createXYChart(req, buildFlapperDataset(req)), calcDefaultSize());
        }
        else {

            /*
             * This method is invoked when a user deliberately fires a wrong url with invalid query
             * parameters and it depicts trend showing no. of passed, failed and skipped testcases for all
             * projects.
             */
            ChartUtil.generateGraph(req, rsp, createChart(req, buildDataSet(req)), calcDefaultSize());
        }
    }

    /**
     * Generates a clickable map HTML for {@link #doGraph(StaplerRequest, StaplerResponse)}.
     */
    public void doGraphMap(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (req.checkIfModified(run.getTimestamp(), rsp))
            return;

        /*
         * The Utility method to generate a mapping from chart coordinates to url to redirect to on
         * clicking the trend.
         */
        doGraphMapUtil(req, rsp);
    }

    /**
     * A utility method for constructing a mapping from chart coordinates to the url to redirect to on
     * clicking the trend.
     *
     * @param req HTTP request message for the image of trend.
     * @param rsp HTTP response message for the requested image.
     * @throws IOException In case an exception occurs in
     *                     {@link ChartUtil#generateClickableMap(StaplerRequest, StaplerResponse, JFreeChart, Area)}
     */
    public void doGraphMapUtil(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String projectLevel = getParameter(req, AbstractTestResultAction.PROJECTLEVEL);
        String trendType = getParameter(req, AbstractTestResultAction.TRENDTYPE);
        int index = Arrays.binarySearch(projectList, projectLevel);
        if ((index >= 0 || projectLevel.equals(AbstractTestResultAction.ALLPROJECTS)) && trendType.equals(AbstractTestResultAction.BUILD_ANALYSIS)) {

            /*
             * This method generates a mapping from chart coordinates to url, to redirect to on clicking the
             * trend generated by same conditions in {@link #doGraphUtil(StaplerRequest, StaplerResponse)}
             */
            //ChartUtil.generateClickableMap(req, rsp, createChart(req, buildDataSet(req)), calcDefaultSize());
            ChartUtil.generateClickableMap(req, rsp, createChart(req, buildDataSetPerProject(req)), calcDefaultSize());
        }
        else if ((index >= 0 || projectLevel.equals(AbstractTestResultAction.ALLPROJECTS)) && trendType.equals(AbstractTestResultAction.LENGTHY_TESTS)) {

            /*
             * This method generates a mapping from chart coordinates to url, to redirect to on clicking the
             * trend generated by same conditions in {@link #doGraphUtil(StaplerRequest, StaplerResponse)}
             */
            ChartUtil.generateClickableMap(req, rsp, createChart(req, buildLengthyTestDataset(req)), calcDefaultSize());
        }
        else if ((index >= 0 || projectLevel.equals(AbstractTestResultAction.ALLPROJECTS)) && trendType.equals(AbstractTestResultAction.FLAKY_TESTS)) {

            /*
             * This method generates a mapping from chart coordinates to url, to redirect to on clicking the
             * trend generated by same conditions in {@link #doGraphUtil(StaplerRequest, StaplerResponse)}
             */
            ChartUtil.generateClickableMap(req, rsp, createXYChart(req, buildFlapperDataset(req)), calcDefaultSize());
        }
        else {

            /*
             * This method is invoked when a user deliberately fires a wrong url with invalid query
             * parameters and it generates a mapping for the same scenario as in
             * {@link #doGraphUtil(StaplerRequest, StaplerResponse)}.
             */
            ChartUtil.generateClickableMap(req, rsp, createChart(req, buildDataSet(req)), calcDefaultSize());
        }
    }

    /**
     * A method to extract value of query parameters from url.
     *
     * @param req       The HTTP request message.
     * @param paramName The name of the query parameter to be extracted.
     * @return The extracted value of the of the query parameter.
     * <p>
     * If the user deliberately fires url with less query parameters then those missing query parameters
     * are assigned default values.
     */
    private String getParameter(StaplerRequest req, String paramName) {
        String paramValue = req.getParameter(paramName);
        if (paramValue == null) {

            /*
             * The default values for each of the mandatory query parameter.
             */
            if (paramName.equals(AbstractTestResultAction.FAILUREONLY))
                return AbstractTestResultAction.ISFAILUREONLY;
            else if (paramName.equals(AbstractTestResultAction.PROJECTLEVEL))
                return AbstractTestResultAction.ALLPROJECTS;
            else if (paramName.equals(AbstractTestResultAction.TRENDTYPE))
                return AbstractTestResultAction.BUILD_ANALYSIS;
            else if (paramName.equals(AbstractTestResultAction.METRICNAME))
                return AbstractTestResultAction.THRESHOLD;
            else if (paramName.equals(AbstractTestResultAction.ORDERBY))
                return AbstractTestResultAction.FAILMETRIC;
        }
        return paramValue;
    }

    /**
     * Returns a full path down to a test result
     */
    public String getTestResultPath(TestResult it) {
        return getUrlName() + "/" + it.getRelativePathFrom(null);
    }

    /**
     * Determines the default size of the trend graph.
     * <p>
     * This is default because the query parameter can choose arbitrary size.
     * If the screen resolution is too low, use a smaller size.
     */
    private Area calcDefaultSize() {
        Area res = Functions.getScreenResolution();
        if (res != null && res.width <= 800)
            return new Area(250, 100);
        else
            return new Area(500, 200);
    }

    /**
     * A method to build the dataset to be used for generating trends.
     *
     * @param req The HTTP request message for the trend.
     * @return An object of type {@link CategoryDataset} in which columns are the build numbers to be depicted on
     * x-axis and rows are the different data series that need to be analysed.
     * <p>
     * This method creates {@link CategoryDataset} object and the created object is exactly same as the one
     * created by {@link #buildDataSetPerProject(StaplerRequest)} with the only difference being it does not
     * work for a particular project but only for "AllProjects" option.
     * <p>
     * This method is retained though its functionality is subset of functionality of
     * {@link #buildDataSetPerProject(StaplerRequest)} as it was there in older versions also.
     */
    private CategoryDataset buildDataSet(StaplerRequest req) {
        boolean failureOnly = Boolean.valueOf(getParameter(req, AbstractTestResultAction.FAILUREONLY));

        DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> dsb = new DataSetBuilder<>();

        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[]{run, cap});
                break;
            }
            dsb.add(a.getFailCount(), "failed", new ChartUtil.NumberOnlyBuildLabel(a.run));
            if (!failureOnly) {
                dsb.add(a.getSkipCount(), "skipped", new ChartUtil.NumberOnlyBuildLabel(a.run));
                dsb.add(a.getTotalCount() - a.getFailCount() - a.getSkipCount(), "total", new ChartUtil.NumberOnlyBuildLabel(a.run));
            }
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[]{run, count});
        return dsb.build();
    }

    private void buildDataSetPerProjectUtil(List<CaseResult> tests, boolean allPackages, String projectLevel,
                                            DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> dsb,
                                            AbstractTestResultAction<?> a, String seriesName) {
        String toolTipString = "";
        final int maxToolTipLength = 100;
        boolean generateToolTip = true;
        int count = 0;
        for (CaseResult caseResult : tests) {
            String caseName = caseResult.getFullName();
            if (!allPackages && !caseName.startsWith(projectLevel))
                continue;
            count++;
            caseName = caseResult.getName();
            if (!generateToolTip)
                continue;
            else if (toolTipString.length() + caseName.length() > maxToolTipLength) {
                generateToolTip = false;
                toolTipString += ",...";
                continue;
            }
            if (!toolTipString.equals(""))
                toolTipString += ", ";
            toolTipString += caseName;
        }
        ChartUtil.NumberOnlyBuildLabel label = new ChartUtil.NumberOnlyBuildLabel(a.run);
        dsb.add(count, seriesName, label);

        /*
         * Also being stored in {@link #failToolTip, #skipToolTip, #totalToolTip} in order to generate tooltips on
         * hovering mouse over the trend.
         */
        if (seriesName.equals("failed"))
            failToolTip.put(label, toolTipString);
        else if (seriesName.equals("skipped"))
            skipToolTip.put(label, toolTipString);
        else
            totalToolTip.put(label, toolTipString);
    }

    /**
     * A method to build dataset for the chosen project to generate trends.
     *
     * @param req The HTTP request message for the particular project or all projects for overall
     *            build analysis trend type.
     * @return An object of type {@link CategoryDataset} in which columns are the build numbers to be
     * displayed on x-axis and rows are the different data series that need to be analysed for the chosen
     * project.
     * <p>
     * This method creates {@link CategoryDataset} object for the chosen project with three data series
     * namely "failed" for the no. of failed testcases, "skipped" for number of skipped testcases and
     * "total" which contains no. of passed testcases. As the generated chart is stacked area chart and
     * total is plotted on top of failed and skipped data series so, total(data series) effectively
     * depict total number of testcases in the build.
     */
    private CategoryDataset buildDataSetPerProject(StaplerRequest req) {
        boolean failureOnly = Boolean.valueOf(getParameter(req, AbstractTestResultAction.FAILUREONLY));
        String projectLevel = getParameter(req, AbstractTestResultAction.PROJECTLEVEL);
        boolean allPackages = projectLevel.equals(AbstractTestResultAction.ALLPROJECTS);
        DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> dsb = new DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel>();
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        failToolTip = new ConcurrentHashMap<>();
        skipToolTip = new ConcurrentHashMap<>();
        totalToolTip = new ConcurrentHashMap<>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[]{run, cap});
                break;
            }
            hudson.tasks.junit.TestResult r = a.loadXml();
            List<CaseResult> failedTests = r.getFailedTests();
            buildDataSetPerProjectUtil(failedTests, allPackages, projectLevel, dsb, a, "failed");
            if (!failureOnly) {
                List<CaseResult> skippedTests = r.getSkippedTests();
                buildDataSetPerProjectUtil(skippedTests, allPackages, projectLevel, dsb, a, "skipped");

                List<CaseResult> passedTests = r.getPassedTests();
                buildDataSetPerProjectUtil(passedTests, allPackages, projectLevel, dsb, a, "total");
            }
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[]{run, count});
        return dsb.build();
    }

    /**
     * A method to calculate ewma(exponentially weighted moving average) time for the given testcase and to
     * check whether the testcase took longer to run.
     *
     * @param alpha    As name suggests it is the alpha parameter involved in calculating ewma. It is the
     *                 weight assigned to time taken by the given testcase when last time it passed.
     * @param caseResult       The {@link CaseResult} object representing the testcase for which we need to compute
     *                 ewma time to check whether it took longer to run.
     * @param allTests Hash Map containing all the testcases which passed in any of the previous builds. The
     *                 testcases are key and their ewma Time is the corresponding value.
     * @return 1 if the given testcase took longer to run else returns 0.
     */
    private int calculateLengthyTestsByMean(float alpha, CaseResult caseResult, Map<String, Float> allTests) {
        String testName = caseResult.getFullName();
        int count = 0;
        float ewmaTime = allTests.getOrDefault(testName, 0.0f);

        /*
         * If this the first build in which the given testcase passed then the testcase won't be considered
         * to be taking longer to run in this build.
         */
        if (allTests.containsKey(testName) && caseResult.getDuration() > ewmaTime) {
            count++;
        }

        /*
         * If this the first build in which given testcase passed then its ewma time is equal to the
         * duration it took run and from hereon the ewma time will be calculated based upon the formula in
         * else clause and will be rounded to 5 decimal places.
         */
        if (!allTests.containsKey(testName)) {
            allTests.put(testName, caseResult.getDuration());
        }
        else {
            ewmaTime = alpha * caseResult.getDuration() + (1 - alpha) * ewmaTime;
            ewmaTime = (1.0f * Math.round(100000 * ewmaTime)) / 100000;
            allTests.put(testName, ewmaTime);
        }
        return count;
    }

    /**
     * A method to determine whether a passed testcase took longer to run based upon the max time it took
     * to run among all the previous builds.
     *
     * @param caseResult       The {@link CaseResult} object representing the testcase for which we need to determine
     *                 whether it took longer to run in this build.
     * @param allTests Hash Map containing all the testcases which passed in any of the previous builds. The
     *                 testcases are key and the max time they took among all previous builds is the
     *                 corresponding value.
     * @return 1 if the given testcase took longer to run else returns 0.
     */
    private int calculateLengthyTestsByMax(CaseResult caseResult, Map<String, Float> allTests) {
        String testName = caseResult.getFullName();
        int count = 0;
        float maxTime = allTests.getOrDefault(testName, 0.0f);

        /*
         * If this the first build in which the given testcase passed then the testcase won't be considered
         * to be taking longer to run in this build.
         */
        if (allTests.containsKey(testName) && caseResult.getDuration() > maxTime) {
            count++;
        }
        maxTime = Math.max(maxTime, caseResult.getDuration());
        allTests.put(testName, maxTime);
        return count;
    }

    /**
     * A method to determine whether a passed testcase took longer to run based upon the time it took to
     * run in the previous build in which it passed.
     *
     * @param caseResult       The {@link CaseResult} object representing the testcase for which we need to determine
     *                 whether it took longer to run in this build.
     * @param allTests Hash Map containing all the testcases which passed in any of the previous builds. The
     *                 testcases are key and the time they took in the previous build in which they passed
     *                 are the corresponding values.
     * @return 1 if the given testcase took longer to run else returns 0.
     */
    private int calculateLengthyTestsByPrev(CaseResult caseResult, Map<String, Float> allTests) {
        String testName = caseResult.getFullName();
        int count = 0;
        float prevTime = allTests.getOrDefault(testName, 0.0f);

        /*
         * If this the first build in which the given testcase passed then the testcase won't be considered
         * to be taking longer to run in this build.
         */
        if (allTests.containsKey(testName) && caseResult.getDuration() > prevTime) {
            count++;
        }
        prevTime = caseResult.getDuration();
        allTests.put(testName, prevTime);
        return count;
    }

    /**
     * A method to determine whether a passed testcase took longer to run based upon whether the time it
     * took to run is greater than the predefined threshold.
     *
     * @param threshold The threshold which classifies a testcase as taking longer to run if the testcase
     *                  takes more than the threshold amount of time to run.
     * @param cr        The {@link CaseResult} object representing the testcase for which we need to determine
     *                  whether it took longer to run in this build.
     * @return 1 if the given testcase took longer to run else returns 0.
     */
    private int calculateLengthyTestsByThreshold(float threshold, CaseResult cr) {
        if (cr.getDuration() > threshold) {
            return 1;
        }
        return 0;
    }

    /**
     * A method to build dataset for the chosen project in order to generate trends for the analysis of
     * testcases which took longer to run.
     *
     * @param req The HTTP request message for the particular project or all projects for "lengthy tests"
     *            trend type.
     * @return An object of type {@link CategoryDataset} in which columns are the build numbers to be
     * displayed on x-axis and row is the data series depicting no. of passed testcases which took
     * longer to run in the respective builds.
     * <p>
     * This method creates {@link CategoryDataset} object for the chosen project with data series named
     * "Lengthy Tests".
     */
    private CategoryDataset buildLengthyTestDataset(StaplerRequest req) {
        String projectLevel = getParameter(req, AbstractTestResultAction.PROJECTLEVEL);
        String metricName = getParameter(req, AbstractTestResultAction.METRICNAME);
        boolean allPackages = projectLevel.equals(AbstractTestResultAction.ALLPROJECTS);
        DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> dsb = new DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel>();
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        lengthyToolTip = new ConcurrentHashMap<>();

        /*
         * A stack is used to traverse the builds in ascending order of build number. First traverse the
         * builds in descending order of build number and push each of the builds onto the stack. Next
         * pop each element of the stack and traverse the builds in ascending order of build number.
         */
        Deque<AbstractTestResultAction<?>> stack = new ArrayDeque<AbstractTestResultAction<?>>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[]{run, cap});
                break;
            }
            stack.push(a);
        }

        /*
         * A hash map for storing mapping of a testcase to the numerical value of metric used for determining
         * whether the testcase is taking longer to run.
         */
        Map<String, Float> allTests = new HashMap<String, Float>();
        while (!stack.isEmpty()) {
            AbstractTestResultAction<?> a = stack.peek();
            hudson.tasks.junit.TestResult r = a.loadXml();
            List<CaseResult> passedTests = r.getPassedTests();
            int lengthyTestCount = 0;
            boolean generateToolTip = true;
            final int maxToolTipLength = 100;
            String toolTipString = "";
            for (CaseResult caseResult : passedTests) {
                String caseName = caseResult.getFullName();
                if (!allPackages && !caseName.startsWith(projectLevel))
                    continue;
                int moreLengthyTests = 0;

                /*
                 * Default metric for determining whether a testcase took longer to run is "mean" i.e.
                 * metric using exponentially weighted moving average of test duration till the previous
                 * build.
                 * As of now only default metric i.e. "mean" is enabled but other metrics can also be
                 * enabled by including them in the drop down menu provided on Jenkins UI.
                 */
                if (metricName.equals(AbstractTestResultAction.THRESHOLD)) {
                    final float threshold = 0.002f;
                    moreLengthyTests += calculateLengthyTestsByThreshold(threshold, caseResult);
                }
                else if (metricName.equals(AbstractTestResultAction.MAX)) {
                    moreLengthyTests += calculateLengthyTestsByMax(caseResult, allTests);
                }
                else if (metricName.equals(AbstractTestResultAction.PREV)) {
                    moreLengthyTests += calculateLengthyTestsByPrev(caseResult, allTests);
                }
                else {
                    final float alpha = 0.5f;
                    moreLengthyTests += calculateLengthyTestsByMean(alpha, caseResult, allTests);
                }
                lengthyTestCount += moreLengthyTests;
                caseName = caseResult.getName();
                if (!generateToolTip || moreLengthyTests == 0)
                    continue;
                else if (toolTipString.length() + caseName.length() > maxToolTipLength) {
                    generateToolTip = false;
                    toolTipString += ",...";
                    continue;
                }
                if (!toolTipString.equals(""))
                    toolTipString += ", ";
                toolTipString += caseName;
            }
            ChartUtil.NumberOnlyBuildLabel label = new ChartUtil.NumberOnlyBuildLabel(a.run);
            dsb.add(lengthyTestCount, "Lengthy Tests", label);

            /*
             * Also being stored in {@link #lengthyToolTip} in order to generate tooltips on hovering mouse
             * over the trend.
             */
            lengthyToolTip.put(label, toolTipString);
            stack.pop();
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[]{run, count});
        return dsb.build();
    }

    private void shiftBuildHistoryUtil(ArrayDeque<Pair<AbstractTestResultAction<?>, HashSet<Integer>>> buildHistory,
                                       Map<Integer, ArrayDeque<AbstractTestResultAction<?>>> testsHistory) {
        Pair<AbstractTestResultAction<?>, HashSet<Integer>> lastBuild = buildHistory.peek();
        buildHistory.remove();
        AbstractTestResultAction<?> a = lastBuild.first;
        HashSet<Integer> buildSet = lastBuild.second;
        int count = 0;
        for (Integer testCaseIndex : buildSet) {
            ArrayDeque<AbstractTestResultAction<?>> testHistory = testsHistory.get(testCaseIndex);
            while (testHistory != null && !testHistory.isEmpty() && testHistory.peek().run.number >= a.run.number) {
                testHistory.remove();
            }
            if (testHistory != null && !testHistory.isEmpty()) {
                count++;
            }
        }
        flapperCountToolTip.put(a.run.number, count);
    }

    private XYDataset buildFlapperDataset(StaplerRequest req) {
        String projectLevel = getParameter(req, AbstractTestResultAction.PROJECTLEVEL);
        boolean allPackages = projectLevel.equals(AbstractTestResultAction.ALLPROJECTS);
        String orderBy = getParameter(req, AbstractTestResultAction.ORDERBY);
        XYSeriesCollection dataset = new XYSeriesCollection();
        List<XYSeries> failSeries = new ArrayList<>();
        int testsToDisplay = 20;
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        Map<Integer, ArrayList<Integer>> testInfo = new HashMap<Integer, ArrayList<Integer>>();
        Map<String, Integer> testCaseIndex = new HashMap<>();
        mostFailedTestCases = new ArrayList<String>();
        XYSeries xySeries = new XYSeries(0);
        flapperCountToolTip = new HashMap<>();
        final int buildHistorySize = 10;
        ArrayDeque<Pair<AbstractTestResultAction<?>, HashSet<Integer>>> buildHistory = new ArrayDeque<>();
        Map<Integer, ArrayDeque<AbstractTestResultAction<?>>> testsHistory = new HashMap<>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[]{run, cap});
                break;
            }
            if (this.run.number - a.run.number + 1 > buildHistorySize) {
                shiftBuildHistoryUtil(buildHistory, testsHistory);
            }
            HashSet<Integer> buildSet = new HashSet<>();
            hudson.tasks.junit.TestResult r = a.loadXml();
            List<CaseResult> tests = r.getFailedTests();
            for (CaseResult caseResult : tests) {
                String caseName = caseResult.getFullName();
                if (!allPackages && !caseName.startsWith(projectLevel))
                    continue;
                if (!testCaseIndex.containsKey(caseName)) {
                    int index = testCaseIndex.size() + 1;
                    testCaseIndex.put(caseName, index);
                    testInfo.put(index, new ArrayList<Integer>());
                    List<Integer> infoList = testInfo.get(index);
                    infoList.add(0);
                    infoList.add(0);
                    infoList.add(-1);
                }
                int index = testCaseIndex.get(caseName);
                List<Integer> infoList = testInfo.get(index);
                infoList.set(0, infoList.get(0) + 1);
                if (infoList.get(2) == 1) {
                    infoList.set(1, infoList.get(1) + 1);
                    ArrayDeque<AbstractTestResultAction<?>> testHistory = testsHistory.getOrDefault(index, new ArrayDeque<>());
                    testHistory.add(a);
                    testsHistory.put(index, testHistory);
                }
                infoList.set(2, 0);
                infoList.add(a.run.number);
                buildSet.add(index);
            }
            tests = r.getPassedTests();
            for (CaseResult caseResult : tests) {
                String caseName = caseResult.getFullName();
                if (!allPackages && !caseName.startsWith(projectLevel))
                    continue;
                Integer index = testCaseIndex.get(caseName);
                if (index != null) {
                    List<Integer> infoList = testInfo.get(index);
                    infoList.set(2, 1);
                }
            }
            xySeries.add(a.run.number, null);
            buildHistory.add(new Pair<>(a, buildSet));
        }
        while (!buildHistory.isEmpty()) {
            shiftBuildHistoryUtil(buildHistory, testsHistory);
        }
        List<Pair<Integer, String>> testFailCount = new ArrayList<>();
        for (String caseName : testCaseIndex.keySet()) {
            int index = testCaseIndex.get(caseName);
            if (orderBy.equals(AbstractTestResultAction.FLAPMETRIC)) {
                testFailCount.add(new Pair<Integer, String>(testInfo.get(index).get(1), caseName));
            }
            else {
                testFailCount.add(new Pair<Integer, String>(testInfo.get(index).get(0), caseName));
            }
        }
        testFailCount.sort(new PairComparator<Integer, String>());
        testsToDisplay = Math.min(testsToDisplay, testFailCount.size());
        flapperInfo = new HashMap<>();
        mostFailedTestCases = new ArrayList<>(testsToDisplay);
        xySeries.add(this.run.number + 0.5, testsToDisplay + 0.5);
        failSeries.add(xySeries);
        for (int i = 1; i <= testsToDisplay; i++) {
            xySeries = new XYSeries(i);
            mostFailedTestCases.add("");
            failSeries.add(xySeries);
        }
        for (int testIndex = 1; testIndex <= testsToDisplay; testIndex++) {
            Pair<Integer, String> testCase = testFailCount.get(testIndex - 1);
            String caseName = testCase.second;
            int y = testsToDisplay - testIndex + 1;
            int index = testCaseIndex.get(caseName);
            List<Integer> infoList = testInfo.get(index);
            mostFailedTestCases.set(y - 1, caseName);
            ArrayList<Integer> flapperInfoList = new ArrayList<>();
            flapperInfoList.add(infoList.get(0));
            flapperInfoList.add(infoList.get(1));
            flapperInfo.put(y, flapperInfoList);
            for (int i = 3; i < infoList.size(); i++) {
                int x = infoList.get(i);
                if (i > 3) {
                    int previousX = infoList.get(i - 1);
                    if (previousX - x > 1) {
                        xySeries = failSeries.get(testIndex);
                        xySeries.add(x + 1, null);
                    }
                }
                xySeries = failSeries.get(testIndex);
                xySeries.add(x, y);
            }
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[]{run, count});
        for (int testIndex = 0; testIndex <= testsToDisplay; testIndex++) {
            dataset.addSeries(failSeries.get(testIndex));
        }
        return dataset;
    }

    /**
     * A method to get y-axis/range axis label for the trend.
     *
     * @param req The HTTP request message.
     * @return Y-axis/range axis label as a string.
     */
    private String getYAxisLabel(StaplerRequest req) {
        String trendType = getParameter(req, AbstractTestResultAction.TRENDTYPE);
        if (trendType.equals(AbstractTestResultAction.BUILD_ANALYSIS) || trendType.equals(AbstractTestResultAction.LENGTHY_TESTS) || trendType.equals(AbstractTestResultAction.FLAKY_TESTS))
            return "Count";
        else
            return "count";
    }

    /**
     * Method to create and render trends on Jenkins UI.
     *
     * @param req     The HTTP request message.
     * @param dataset The dataset containing each of the data series to be rendered on the generated chart.
     * @return An object of type {@link JFreeChart} which contains information about all the properties of
     * chart as well as the renderer object.
     * <p>
     * The renderer object has overridden {@link StackedAreaRenderer2#generateURL(CategoryDataset, int, int)}
     * and {@link StackedAreaRenderer2#generateToolTip(CategoryDataset, int, int)} for generating custom url
     * for clickable map and for generating custom tool tip to display on hovering mouse over the chart
     * respectively.
     */
    private JFreeChart createChart(StaplerRequest req, CategoryDataset dataset) {

        final String relPath = getRelPath(req);
        String yaxis = getYAxisLabel(req);

        final JFreeChart chart = ChartFactory.createStackedAreaChart(
                null,                   // chart title
                null,                   // unused
                yaxis,                  // range axis label
                dataset,                  // data
                PlotOrientation.VERTICAL, // orientation
                false,                     // include legend
                true,                     // tooltips
                false                     // urls
        );

        // NOW DO SOME OPTIONAL CUSTOMISATION OF THE CHART...

        // set the background color for the chart...

//        final StandardLegend legend = (StandardLegend) chart.getLegend();
//        legend.setAnchor(StandardLegend.SOUTH);

        chart.setBackgroundPaint(Color.white);

        final CategoryPlot plot = chart.getCategoryPlot();

        // plot.setAxisOffset(new Spacer(Spacer.ABSOLUTE, 5.0, 5.0, 5.0, 5.0));
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        plot.setForegroundAlpha(0.8f);
//        plot.setDomainGridlinesVisible(true);
//        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.black);

        CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
        plot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.0);
        domainAxis.setCategoryMargin(0.0);

        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        StackedAreaRenderer ar;

        /*
         * "Messages" is present inside resources/hudson/tasks/test/Resource Bundle 'Messages' as properties
         * file and "Messages" class get constructed on building the plugin inside target/generated-sources/
         * localizer/hudson/tasks/test.
         * The Messages class is used for the purpose of internationalization.
         */
        if (getParameter(req, AbstractTestResultAction.TRENDTYPE).equals(AbstractTestResultAction.BUILD_ANALYSIS)) {
            ar = new StackedAreaRenderer2() {
                @Override
                public String generateURL(CategoryDataset dataset, int row, int column) {
                    ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    return relPath + label.getRun().getNumber() + "/testReport/";
                }

                @Override
                public String generateToolTip(CategoryDataset dataset, int row, int column) {
                    ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    switch (row) {
                        case 0:
                            return String.valueOf(Messages.AbstractTestResultAction_perProject(label.getRun().getDisplayName(), failToolTip.get(label)));
                        case 1:
                            return String.valueOf(Messages.AbstractTestResultAction_perProject(label.getRun().getDisplayName(), skipToolTip.get(label)));
                        default:
                            return String.valueOf(Messages.AbstractTestResultAction_perProject(label.getRun().getDisplayName(), totalToolTip.get(label)));
                    }
                }
            };
        }
        else if (getParameter(req, AbstractTestResultAction.TRENDTYPE).equals(AbstractTestResultAction.LENGTHY_TESTS)) {
            ar = new StackedAreaRenderer2() {
                @Override
                public String generateURL(CategoryDataset dataset, int row, int column) {
                    ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    return relPath + label.getRun().getNumber() + "/testReport/";
                }

                @Override
                public String generateToolTip(CategoryDataset dataset, int row, int column) {
                    ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    return String.valueOf(Messages.AbstractTestResultAction_lengthyTests(label.getRun().getDisplayName(), lengthyToolTip.get(label)));
                }
            };
        }
        else {
            ar = new StackedAreaRenderer2() {
                @Override
                public String generateURL(CategoryDataset dataset, int row, int column) {
                    ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    return relPath + label.getRun().getNumber() + "/testReport/";
                }

                @Override
                public String generateToolTip(CategoryDataset dataset, int row, int column) {
                    ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    AbstractTestResultAction a = label.getRun().getAction(AbstractTestResultAction.class);
                    switch (row) {
                        case 0:
                            return String.valueOf(Messages.AbstractTestResultAction_fail(label.getRun().getDisplayName(), a.getFailCount()));
                        case 1:
                            return String.valueOf(Messages.AbstractTestResultAction_skip(label.getRun().getDisplayName(), a.getSkipCount()));
                        default:
                            return String.valueOf(Messages.AbstractTestResultAction_test(label.getRun().getDisplayName(), a.getTotalCount()));
                    }
                }
            };
        }
        plot.setRenderer(ar);
        ar.setSeriesPaint(0, ColorPalette.RED); // First data series.
        ar.setSeriesPaint(1, ColorPalette.YELLOW); // Second data series.
        ar.setSeriesPaint(2, ColorPalette.BLUE); // third data series.

        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));

        return chart;
    }

    private String getRelPath(StaplerRequest req) {
        String relPath = req.getParameter("rel");
        if (relPath == null)
            return "";
        return relPath;
    }

    private JFreeChart createXYChart(StaplerRequest req, XYDataset dataset) {
        final String relPath = getRelPath(req);
        final String yAxis = "Test Cases";
        final String xAxis = "Build Number";
        final JFreeChart chart = ChartFactory.createXYLineChart(
                null,         // chart title
                xAxis,             // domain axis label
                yAxis,            // range axis label
                dataset,          // data
                PlotOrientation.VERTICAL, // orientation
                false,      // legends
                true,       // tooltips
                false);       // urls

        chart.setBackgroundPaint(Color.WHITE);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        plot.setForegroundAlpha(0.8f);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.black);
        plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        NumberAxis domainAxis = (NumberAxis) plot.getDomainAxis();
        domainAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.0);
        domainAxis.setTickMarkOutsideLength(5.0f);
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        rangeAxis.setTickLabelsVisible(false);
        rangeAxis.setAutoRangeIncludesZero(true);
        rangeAxis.setTickMarkOutsideLength(5.0f);
        rangeAxis.setLabelInsets(new RectangleInsets(10.0, 10.0, 10.0, 10.0));

        int testsToDisplay = plot.getSeriesCount() - 1;

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();

        XYToolTipGenerator toolTipGenerator = new XYToolTipGenerator2() {
            @Override
            public String generateToolTip(XYDataset dataset, int series, int item) {
                int x = (int) (dataset.getXValue(series, item) + 0.5);
                int y = (int) (dataset.getYValue(series, item) + 0.5);
                String caseName = mostFailedTestCases.get(y - 1) + "\n";
                String totalFailCount = flapperInfo.get(y).get(0) + "\n";
                String flapCount = flapperInfo.get(y).get(1) + "\n";
                String flapperCount = flapperCountToolTip.get(x).toString();
                String build = "#" + x;
                return String.valueOf(Messages.AbstractTestResultAction_testInfo(build, caseName, totalFailCount, flapCount, flapperCount));
            }
        };

        XYURLGenerator urlGenerator = new XYToolTipGenerator2() {
            @Override
            public String generateURL(XYDataset dataset, int series, int item) {
                int x = (int) (dataset.getXValue(series, item) + 0.5);
                return relPath + x + "/testReport/";
            }
        };

        Marker flapCountMarker, flapperCountMarker;
        Font markerFont = new Font("Arial", Font.BOLD, 12);

        for (int testIndex = 0; testIndex <= testsToDisplay; testIndex++) {
            boolean isShapeVisible = !(testIndex == 0);
            renderer.setSeriesShapesFilled(testIndex, isShapeVisible);
            renderer.setSeriesShapesVisible(testIndex, isShapeVisible);
            renderer.setSeriesToolTipGenerator(testIndex, toolTipGenerator);
            renderer.setSeriesPaint(testIndex, Color.RED);
            renderer.setSeriesStroke(testIndex, new BasicStroke(4.0f));
//            if (testIndex == 0)
//                continue;
//            flapCountMarker = new ValueMarker(testIndex);
//            String flapCount = flapperInfo.get(testIndex).get(1).toString();
//            flapCountMarker.setPaint(Color.BLACK);
//            flapCountMarker.setLabel(flapCount);
//            flapCountMarker.setLabelAnchor(RectangleAnchor.BOTTOM_RIGHT);
//            flapCountMarker.setLabelTextAnchor(TextAnchor.TOP_RIGHT);
//            flapCountMarker.setLabelFont(markerFont);
//            plot.addRangeMarker(flapCountMarker);
        }
//        for (int buildNumber : flapperCountToolTip.keySet()) {
//            flapperCountMarker = new ValueMarker(buildNumber);
//            String flapperCount = flapperCountToolTip.get(buildNumber).toString();
//            flapperCountMarker.setPaint(Color.BLACK);
//            flapperCountMarker.setLabel(flapperCount);
//            flapperCountMarker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
//            flapperCountMarker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
//            flapperCountMarker.setLabelFont(markerFont);
//            plot.addDomainMarker(flapperCountMarker);
//        }
        renderer.setURLGenerator(urlGenerator);
        plot.setRenderer(renderer);

        return chart;
    }

    /**
     * {@link TestObject}s do not have their own persistence mechanism, so updatable data of {@link TestObject}s
     * need to be persisted by the owning {@link AbstractTestResultAction}, and this method and
     * {@link #setDescription(TestObject, String)} provides that logic.
     *
     * <p>
     * The default implementation stores information in the 'this' object.
     *
     * @see TestObject#getDescription()
     */
    protected String getDescription(TestObject object) {
        return descriptions.get(object.getId());
    }

    protected void setDescription(TestObject object, String description) {
        descriptions.put(object.getId(), description);
    }

    public Object readResolve() {
        if (descriptions == null) {
            descriptions = new ConcurrentHashMap<String, String>();
        }

        return this;
    }

    @Extension
    public static final class Summarizer extends Run.StatusSummarizer {
        @Override
        public Run.Summary summarize(Run<?, ?> run, ResultTrend trend) {
            AbstractTestResultAction<?> trN = run.getAction(AbstractTestResultAction.class);
            if (trN == null) {
                return null;
            }
            Boolean worseOverride;
            switch (trend) {
                case NOW_UNSTABLE:
                    worseOverride = false;
                    break;
                case UNSTABLE:
                    worseOverride = true;
                    break;
                case STILL_UNSTABLE:
                    worseOverride = null;
                    break;
                default:
                    return null;
            }
            Run prev = run.getPreviousBuild();
            AbstractTestResultAction<?> trP = prev == null ? null : prev.getAction(AbstractTestResultAction.class);
            if (trP == null) {
                if (trN.getFailCount() > 0) {
                    return new Run.Summary(worseOverride != null ? worseOverride : true, Messages.Run_Summary_TestFailures(trN.getFailCount()));
                }
            }
            else {
                if (trN.getFailCount() != 0) {
                    if (trP.getFailCount() == 0) {
                        return new Run.Summary(worseOverride != null ? worseOverride : true, Messages.Run_Summary_TestsStartedToFail(trN.getFailCount()));
                    }
                    if (trP.getFailCount() < trN.getFailCount()) {
                        return new Run.Summary(worseOverride != null ? worseOverride : true, Messages.Run_Summary_MoreTestsFailing(trN.getFailCount() - trP.getFailCount(), trN.getFailCount()));
                    }
                    if (trP.getFailCount() > trN.getFailCount()) {
                        return new Run.Summary(worseOverride != null ? worseOverride : false, Messages.Run_Summary_LessTestsFailing(trP.getFailCount() - trN.getFailCount(), trN.getFailCount()));
                    }

                    return new Run.Summary(worseOverride != null ? worseOverride : false, Messages.Run_Summary_TestsStillFailing(trN.getFailCount()));
                }
            }
            return null;
        }
    }

    private static final class PairComparator<A extends Comparable<? super A>, B extends Comparable<? super B>> implements Comparator<Pair<A, B>> {
        public int compare(Pair<A, B> pair1, Pair<A, B> pair2) {
            if (pair1.first.compareTo(pair2.first) != 0) {
                return -1 * pair1.first.compareTo(pair2.first);
            }
            else
                return pair1.second.compareTo(pair2.second);
        }
    }

    /**
     * A generic class to encapsulate a pair of objects where each object can be any type.
     *
     * @param <A> Type of the first object.
     * @param <B> Type of the second object.
     */
    private static final class Pair<A, B> {

        /**
         * First object.
         */
        A first;

        /**
         * Second object.
         */
        B second;

        /**
         * A constructor to create a {@link Pair} object containing both the objects
         * {@link Pair#first} and {@link Pair#second} as pair.
         *
         * @param first  The first object conatined in the pair.
         * @param second The second object contained in the pair.
         */
        Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }

    public static class XYToolTipGenerator2 implements XYToolTipGenerator, XYURLGenerator {
        @Override
        public String generateToolTip(XYDataset dataset, int series, int item) {
            return null;
        }

        @Override
        public String generateURL(XYDataset dataset, int series, int item) {
            return null;
        }
    }

    private static final String ISFAILUREONLY = "false";
    private static final String ALLPROJECTS = "AllProjects";
    private static final String BUILD_ANALYSIS = "BuildAnalysis";
    private static final String LENGTHY_TESTS = "LengthyTests";
    private static final String FLAKY_TESTS = "FlakyTests";
    private static final String MEAN = "mean";
    private static final String MAX = "max";
    private static final String PREV = "prev";
    private static final String THRESHOLD = "threshold";
    private static final String FLAPMETRIC = "flap";
    private static final String FAILMETRIC = "fail";
    private static final String FAILUREONLY = "failureOnly";
    private static final String PROJECTLEVEL = "projectLevel";
    private static final String TRENDTYPE = "trendType";
    private static final String METRICNAME = "metricName";
    private static final String ORDERBY = "orderBy";
}
