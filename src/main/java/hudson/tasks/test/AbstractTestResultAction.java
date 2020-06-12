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
import hudson.model.*;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.util.*;
import hudson.util.ChartUtil.NumberOnlyBuildLabel;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.RunAction2;
import jenkins.model.lazy.LazyBuildMixIn;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

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
    public transient Run<?,?> run;
    @Deprecated
    public transient AbstractBuild<?,?> owner;

    private Map<String,String> descriptions = new ConcurrentHashMap<String, String>();

    /**
     * Tool tips for "Overall Build Analysis" trend type.
     */
    private Map<NumberOnlyBuildLabel,Integer> failToolTip, totalToolTip, skipToolTip;

    /**
     * Tool tips for "Lengthy Tests" trend type.
     */
    private Map<NumberOnlyBuildLabel, Integer> lengthyToolTip;

    /**
     * Tool tips for "Flaky Tests" trend type.
     */
    private Map<NumberOnlyBuildLabel,String> flapperToolTip;

    /**
     * Array for storing package hierarchy derived from each testcase, used for selecting a particular
     * project.
     */
    private String[] projectList;

    /** @since 1.545 */
    protected AbstractTestResultAction() {}

    /**
     * @deprecated Use the default constructor and just call {@link Run#addAction} to associate the build with the action.
     * @since 1.2-beta-1
     */
    @Deprecated
    protected AbstractTestResultAction(Run owner) {
        onAttached(owner);
    }

    @Deprecated
    protected AbstractTestResultAction(AbstractBuild owner) {
        this((Run) owner);
    }

    @Override public void onAttached(Run<?, ?> r) {
        this.run = r;
        this.owner = r instanceof AbstractBuild ? (AbstractBuild<?,?>) r : null;
    }

    @Override public void onLoad(Run<?, ?> r) {
        this.run = r;
        this.owner = r instanceof AbstractBuild ? (AbstractBuild<?,?>) r : null;
    }

    /**
     * Gets the number of failed tests.
     */
    @Exported(visibility=2)
    public abstract int getFailCount();

    /**
     * Gets the number of skipped tests.
     */
    @Exported(visibility=2)
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
    @Exported(visibility=2)
    public abstract int getTotalCount();

    /**
     * Gets the diff string of failures.
     */
    public final String getFailureDiffString() {
        T prev = getPreviousResult();
        if(prev==null)  return "";  // no record

        return " / "+Functions.getDiffString(this.getFailCount()-prev.getFailCount());
    }

    public String getDisplayName() {
        return Messages.AbstractTestResultAction_getDisplayName();
    }

    @Exported(visibility=2)
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
        Localizable description, displayName = Messages._AbstractTestResultAction_getDisplayName();
        if (totalCount == 0) {
        	description = Messages._AbstractTestResultAction_zeroTestDescription(displayName);
        } else {
        	description = Messages._AbstractTestResultAction_TestsDescription(displayName, failCount, totalCount);
        }
        return new HealthReport(score, description);
    }

    /**
     * Returns how much to scale the test related health by.
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
        return (T)getPreviousResult(getClass(), true);
    }

    private <U extends AbstractTestResultAction> U getPreviousResult(Class<U> type, boolean eager) {
        Run<?,?> b = run;
        Set<Integer> loadedBuilds;
        if (!eager && run.getParent() instanceof LazyBuildMixIn.LazyLoadingJob) {
            loadedBuilds = ((LazyBuildMixIn.LazyLoadingJob<?,?>) run.getParent()).getLazyBuildMixIn()._getRuns().getLoadedBuilds().keySet();
        } else {
            loadedBuilds = null;
        }
        while(true) {
            b = loadedBuilds == null || loadedBuilds.contains(b.number - /* assuming there are no gaps */1) ? b.getPreviousBuild() : null;
            if(b==null)
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
            TestResult testResult = (TestResult)getResult();
            return testResult.findCorrespondingResult(test.getId());
        }

        return null;
    }

    public TestResult findCorrespondingResult(String id) {
        final Object testResult = getResult();
        if (!(testResult instanceof TestResult)) {
            return null;
        }
        return ((TestResult)testResult).findCorrespondingResult(id);
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

    public hudson.tasks.junit.TestResult loadXml(){return null;}

    /**
     * A method for getting the list of packages for all levels of hierarchy.
     * @return Array of packages of all hierarchies.
     */
    public String[] getProjectList(){
        /**
         * If project list is already ready no need to construct again.
         */
        if(projectList!=null) return projectList;
        hudson.tasks.junit.TestResult r = loadXml();
        Collection<SuiteResult> suiteList = r.getSuites();
        /**
         * A set for the package names.
         */
        Set<String> projectSet = new HashSet<String>();
        for(SuiteResult sr: suiteList){
            for(CaseResult cr: sr.getCases()){
                String caseName = cr.getFullName();
                String projectName = "";
                /**
                 * Splitting package by treating "." as separator.
                 */
                String[] packageTree = caseName.split("[.]");
                /**
                 * Iterating till the length-2 of packageTree array to exclude class name and test name.
                 */
                for(int i=0;i<packageTree.length-2;i++){
                    if(!projectName.isEmpty()) projectName+='.';
                    projectName+=packageTree[i];
                    projectSet.add(projectName);
                }
            }
        }
        /**
         * Converting the set of package names to the corresponding array
         * and finally sorting it in ascending order.
         */
        int size = projectSet.size();
        projectList = new String[size];
        int indx = 0;
        for(String projectName: projectSet){
            projectList[indx] = projectName;
            indx++;
        }
        Arrays.sort(projectList);
        return projectList;
    }

    /**
     * Generates a PNG image for the test result trend.
     */
    public void doGraph( StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(ChartUtil.awtProblemCause!=null) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath()+"/images/headless.png");
            return;
        }

        if(req.checkIfModified(run.getTimestamp(),rsp))
            return;

        /**
         * Utility method for creating various test result trends.
         */
        doGraphUtil(req, rsp);
    }

    /**
     * A utility method for constructing trends based upon the query parameters passed in the
     * request message.
     * @param req HTTP request message for the image of trend.
     * @param rsp HTTP response message for the requested image.
     * @throws IOException in case an exception occurs in
     * {@link ChartUtil#generateGraph(StaplerRequest, StaplerResponse, JFreeChart, Area)}
     */
    public void doGraphUtil(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String projectLevel = getParameter(req,PROJECTLEVEL);
        String trendType = getParameter(req,TRENDTYPE);
        /**
         * A binary search for verifying whether the given project level is valid or not. If found in the
         * array or is equal to "AllProjects" the valid else not.
         */
        int indx = Arrays.binarySearch(projectList,projectLevel);
        if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND1)){
            /**
             * This method generates the trend depicting no. of failed, passed and skipped testcases for
             * the specified project or for all projects.
             */
            if(failToolTip ==null) failToolTip = new HashMap<NumberOnlyBuildLabel, Integer>();
            if(totalToolTip ==null) totalToolTip = new HashMap<NumberOnlyBuildLabel, Integer>();
            if(skipToolTip ==null) skipToolTip = new HashMap<NumberOnlyBuildLabel, Integer>();
            ChartUtil.generateGraph(req,rsp,createChart(req,buildDataSetPerProject(req)),calcDefaultSize());
        }
        else if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND2)){
            /**
             * This method generates the trends depicting no. of passed testcases which took longer duration
             * to run in the given build.
             */
            if(lengthyToolTip ==null) lengthyToolTip = new HashMap<NumberOnlyBuildLabel, Integer>();
            ChartUtil.generateGraph(req,rsp,createChart(req,buildLengthyTestDataset(req)),calcDefaultSize());
        }
        else if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND3)){
            /**
             * This method generates the trends depicting no. of passed and failed testcases which were
             * inconsistently failing or passing i.e. flappy behaviour.
             */
            if(flapperToolTip ==null) flapperToolTip = new HashMap<NumberOnlyBuildLabel,String>();
            ChartUtil.generateGraph(req,rsp,createChart(req,buildFlapperTestDataset(req)),calcDefaultSize());
        }
        else{
            /**
             * This method is invoked when a user deliberately fires a wrong url with invalid query
             * parameters and it depicts trend showing no. of passed, failed and skipped testcases for all
             * projects.
             */
            ChartUtil.generateGraph(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
        }
    }

    /**
     * Generates a clickable map HTML for {@link #doGraph(StaplerRequest, StaplerResponse)}.
     */
    public void doGraphMap( StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(req.checkIfModified(run.getTimestamp(),rsp))
            return;
        /**
         * The Utility method to generate a mapping from chart coordinates to url to redirect to on
         * clicking the trend.
         */
        doGraphMapUtil(req,rsp);
    }

    /**
     * A utility method for constructing a mapping from chart coordinates to the url to redirect to on
     * clicking the trend.
     * @param req HTTP request message for the image of trend.
     * @param rsp HTTP response message for the requested image.
     * @throws IOException In case an exception occurs in
     * {@link ChartUtil#generateClickableMap(StaplerRequest, StaplerResponse, JFreeChart, Area)}
     */
    public void doGraphMapUtil(StaplerRequest req, StaplerResponse rsp) throws IOException{
        String projectLevel = getParameter(req,PROJECTLEVEL);
        String trendType = getParameter(req,TRENDTYPE);
        int indx = Arrays.binarySearch(projectList,projectLevel);
        if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND1)){
            /**
             * This method generates a mapping from chart coordinates to url, to redirect to on clicking the
             * trend generated by same conditions in {@link #doGraphUtil(StaplerRequest, StaplerResponse)}
             */
            if(failToolTip ==null) failToolTip = new HashMap<NumberOnlyBuildLabel, Integer>();
            if(totalToolTip ==null) totalToolTip = new HashMap<NumberOnlyBuildLabel, Integer>();
            if(skipToolTip ==null) skipToolTip = new HashMap<NumberOnlyBuildLabel, Integer>();
            ChartUtil.generateClickableMap(req,rsp,createChart(req,buildDataSetPerProject(req)),calcDefaultSize());
        }
        else if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND2)){
            /**
             * This method generates a mapping from chart coordinates to url, to redirect to on clicking the
             * trend generated by same conditions in {@link #doGraphUtil(StaplerRequest, StaplerResponse)}
             */
            if(lengthyToolTip ==null) lengthyToolTip = new HashMap<NumberOnlyBuildLabel, Integer>();
            ChartUtil.generateClickableMap(req,rsp,createChart(req,buildLengthyTestDataset(req)),calcDefaultSize());
        }
        else if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND3)){
            /**
             * This method generates a mapping from chart coordinates to url, to redirect to on clicking the
             * trend generated by same conditions in {@link #doGraphUtil(StaplerRequest, StaplerResponse)}
             */
            if(flapperToolTip ==null) flapperToolTip = new HashMap<NumberOnlyBuildLabel,String>();
            ChartUtil.generateClickableMap(req,rsp,createChart(req,buildFlapperTestDataset(req)),calcDefaultSize());
        }
        else{
            /**
             * This method is invoked when a user deliberately fires a wrong url with invalid query
             * parameters and it generates a mapping for the same scenario as in
             * {@link #doGraphUtil(StaplerRequest, StaplerResponse)}.
             */
            ChartUtil.generateClickableMap(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
        }
    }

    /**
     * A method to extract value of query parameters from url.
     * @param req The HTTP request message.
     * @param paramName The name of the query parameter to be extracted.
     * @return The extracted value of the of the query parameter.
     * <p>
     * If the user deliberately fires url with less query parameters then those missing query parameters
     * are assigned default values.
     */
    private String getParameter(StaplerRequest req, String paramName){
        String paramValue = req.getParameter(paramName);
        if(paramValue==null){
            /**
             * The default values for each of the mandatory query parameter.
             */
            if(paramName.equals(FAILUREONLY)) return ISFAILUREONLY;
            else if(paramName.equals(PROJECTLEVEL)) return ALLPROJECTS;
            else if(paramName.equals(TRENDTYPE)) return TREND1;
            else if(paramName.equals(METRICNAME)) return METRIC4;
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
     *
     * This is default because the query parameter can choose arbitrary size.
     * If the screen resolution is too low, use a smaller size.
     */
    private Area calcDefaultSize() {
        Area res = Functions.getScreenResolution();
        if(res!=null && res.width<=800)
            return new Area(250,100);
        else
            return new Area(500,200);
    }

    /**
     * A method to build the dataset to be used for generating trends.
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
        boolean failureOnly = Boolean.valueOf(getParameter(req,FAILUREONLY));

        DataSetBuilder<String,NumberOnlyBuildLabel> dsb = new DataSetBuilder<String,NumberOnlyBuildLabel>();

        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[] {run, cap});
                break;
            }
            dsb.add( a.getFailCount(), "failed", new NumberOnlyBuildLabel(a.run));
            if(!failureOnly) {
                dsb.add( a.getSkipCount(), "skipped", new NumberOnlyBuildLabel(a.run));
                dsb.add( a.getTotalCount()-a.getFailCount()-a.getSkipCount(),"total", new NumberOnlyBuildLabel(a.run));
            }
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
        return dsb.build();
    }

    /**
     * A method to build dataset for the chosen project to generate trends.
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
    private CategoryDataset buildDataSetPerProject(StaplerRequest req){
        boolean failureOnly = Boolean.valueOf(getParameter(req,FAILUREONLY));
        String projectLevel = getParameter(req,PROJECTLEVEL);
        boolean allPackages = projectLevel.equals(ALLPROJECTS);
        DataSetBuilder<String,NumberOnlyBuildLabel> dsb = new DataSetBuilder<String,NumberOnlyBuildLabel>();
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[] {run, cap});
                break;
            }
            hudson.tasks.junit.TestResult r = a.loadXml();
            List<CaseResult> failedTests = r.getFailedTests();
            int failCount = 0, passCount = 0, skipCount = 0;
            for(CaseResult cr: failedTests){
                String caseName = cr.getFullName();
                if(allPackages||(!allPackages&&caseName.startsWith(projectLevel))){
                    failCount++;
                }
            }
            NumberOnlyBuildLabel label = new NumberOnlyBuildLabel(a.run);
            dsb.add(failCount, "failed", label);
            /**
             * Also being stored in {@link #failToolTip} in order to generate tooltips on hovering mouse over the
             * trend.
             */
            failToolTip.put(label,failCount);
            if(!failureOnly) {
                List<CaseResult> passedTests = r.getPassedTests();
                for(CaseResult cr: passedTests){
                    String caseName = cr.getFullName();
                    if(allPackages||(!allPackages&&caseName.startsWith(projectLevel))){
                        passCount++;
                    }
                }
                List<CaseResult> skippedTests = r.getSkippedTests();
                for(CaseResult cr: skippedTests){
                    String caseName = cr.getFullName();
                    if(allPackages||(!allPackages&&caseName.startsWith(projectLevel))){
                        skipCount++;
                    }
                }
                label = new NumberOnlyBuildLabel(a.run);
                dsb.add( skipCount, "skipped", label);
                /**
                 * Also being stored in {@link #skipToolTip} in order to generate tooltips on hovering mouse over the
                 * trend.
                 */
                skipToolTip.put(label,skipCount);
                label = new NumberOnlyBuildLabel(a.run);
                dsb.add( passCount,"total", label);
                /**
                 * Also being stored in {@link #totalToolTip} in order to generate tooltips on hovering mouse over the
                 * trend.
                 */
                totalToolTip.put(label,passCount+failCount+skipCount);
            }
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
        return dsb.build();
    }

    /**
     * A method to calculate ewma(exponentially weighted moving average) time for the given testcase and to
     * check whether the testcase took longer to run.
     * @param alpha As name suggests it is the alpha parameter involved in calculating ewma. It is the
     *              weight assigned to time taken by the given testcase when last time it passed.
     * @param cr The {@link CaseResult} object representing the testcase for which we need to compute
     *           ewma time to check whether it took longer to run.
     * @param allTests Hash Map containing all the testcases which passed in any of the previous builds. The
     *                 testcases are key and their ewma Time is the corresponding value.
     * @return 1 if the given testcase took longer to run else returns 0.
     */
    private int calculateLengthyTestsByMean(float alpha, CaseResult cr, Map<String, Float> allTests){
        String testName = cr.getFullName();
        int count = 0;
        float ewmaTime =  allTests.getOrDefault(testName,0.0f);
        /**
         * If this the first build in which the given testcase passed then the testcase won't be considered
         * to be taking longer to run in this build.
         */
        if(allTests.containsKey(testName)&&cr.getDuration()>ewmaTime){
            count++;
        }
        /**
         * If this the first build in which given testcase passed then its ewma time is equal to the
         * duration it took run and from hereon the ewma time will be calculated based upon the formula in
         * else clause and will be rounded to 5 decimal places.
         */
        if(!allTests.containsKey(testName)){
            allTests.put(testName,cr.getDuration());
        }
        else {
            ewmaTime = alpha * cr.getDuration() + (1 - alpha) * ewmaTime;
            ewmaTime = (1.0f*Math.round(100000*ewmaTime))/100000;
            allTests.put(testName,ewmaTime);
        }
        return count;
    }

    /**
     * A method to determine whether a passed testcase took longer to run based upon the max time it took
     * to run among all the previous builds.
     * @param cr The {@link CaseResult} object representing the testcase for which we need to determine
     *           whether it took longer to run in this build.
     * @param allTests Hash Map containing all the testcases which passed in any of the previous builds. The
     *                 testcases are key and the max time they took among all previous builds is the
     *                 corresponding value.
     * @return 1 if the given testcase took longer to run else returns 0.
     */
    private int calculateLengthyTestsByMax(CaseResult cr, Map<String, Float> allTests){
        String testName = cr.getFullName();
        int count = 0;
        float maxTime =  allTests.getOrDefault(testName,0.0f);
        /**
         * If this the first build in which the given testcase passed then the testcase won't be considered
         * to be taking longer to run in this build.
         */
        if(allTests.containsKey(testName)&&cr.getDuration()>maxTime){
            count++;
        }
        maxTime = Math.max(maxTime,cr.getDuration());
        allTests.put(testName,maxTime);
        return count;
    }

    /**
     * A method to determine whether a passed testcase took longer to run based upon the time it took to
     * run in the previous build in which it passed.
     * @param cr The {@link CaseResult} object representing the testcase for which we need to determine
     *           whether it took longer to run in this build.
     * @param allTests Hash Map containing all the testcases which passed in any of the previous builds. The
     *                 testcases are key and the time they took in the previous build in which they passed
     *                 are the corresponding values.
     * @return 1 if the given testcase took longer to run else returns 0.
     */
    private int calculateLengthyTestsByPrev(CaseResult cr, Map<String, Float> allTests){
        String testName = cr.getFullName();
        int count = 0;
        float prevTime =  allTests.getOrDefault(testName,0.0f);
        /**
         * If this the first build in which the given testcase passed then the testcase won't be considered
         * to be taking longer to run in this build.
         */
        if(allTests.containsKey(testName)&&cr.getDuration()>prevTime){
            count++;
        }
        prevTime = cr.getDuration();
        allTests.put(testName,prevTime);
        return count;
    }

    /**
     * A method to determine whether a passed testcase took longer to run based upon whether the time it
     * took to run is greater than the predefined threshold.
     * @param threshold The threshold which classifies a testcase as taking longer to run if the testcase
     *                  takes more than the threshold amount of time to run.
     * @param cr The {@link CaseResult} object representing the testcase for which we need to determine
     *           whether it took longer to run in this build.
     * @return 1 if the given testcase took longer to run else returns 0.
     */
    private int calculateLengthyTestsByThreshold(float threshold, CaseResult cr){
        if(cr.getDuration()>threshold){
            return 1;
        }
        return 0;
    }

    /**
     * A method to build dataset for the chosen project in order to generate trends for the analysis of
     * testcases which took longer to run.
     * @param req The HTTP request message for the particular project or all projects for "lengthy tests"
     *            trend type.
     * @return An object of type {@link CategoryDataset} in which columns are the build numbers to be
     * displayed on x-axis and row is the data series depicting no. of passed testcases which took
     * longer to run in the respective builds.
     * <p>
     * This method creates {@link CategoryDataset} object for the chosen project with data series named
     * "Lengthy Tests".
     */
    private CategoryDataset buildLengthyTestDataset(StaplerRequest req){
        String projectLevel = getParameter(req,PROJECTLEVEL);
        String metricName = getParameter(req,METRICNAME);
        boolean allPackages = projectLevel.equals(ALLPROJECTS);
        DataSetBuilder<String,NumberOnlyBuildLabel> dsb = new DataSetBuilder<String,NumberOnlyBuildLabel>();
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        /**
         * A stack is used to traverse the builds in ascending order of build number. First traverse the
         * builds in descending order of build number and push each of the builds onto the stack. Next
         * pop each element of the stack and traverse the builds in ascending order of build number.
         */
        Deque<AbstractTestResultAction> stack = new ArrayDeque<AbstractTestResultAction>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[]{run, cap});
                break;
            }
            stack.push(a);
        }
        /**
         * A hash map for storing mapping of a testcase to the numerical value of metric used for determining
         * whether the testcase is taking longer to run.
         */
        Map<String,Float> allTests = new HashMap<String,Float>();
        while(!stack.isEmpty()){
            AbstractTestResultAction a = stack.peek();
            hudson.tasks.junit.TestResult r = a.loadXml();
            List<CaseResult> passedTests = r.getPassedTests();
            int lengthyTestCount = 0;
            for(CaseResult cr: passedTests){
                if(!allPackages&&!cr.getFullName().startsWith(projectLevel)) continue;
                /**
                 * Default metric for determining whether a testcase took longer to run is "mean" i.e.
                 * metric using exponentially weighted moving average of test duration till the previous
                 * build.
                 * <p>
                 * As of now only default metric i.e. "mean" is enabled but other metrics can also be
                 * enabled by including them in the drop down menu provided on Jenkins UI.
                 */
                if(metricName.equals(METRIC4)) {
                    float threshold = 0.002f;
                    lengthyTestCount += calculateLengthyTestsByThreshold(threshold, cr);
                }
                else if(metricName.equals(METRIC2)){
                    lengthyTestCount += calculateLengthyTestsByMax(cr, allTests);
                }
                else if(metricName.equals(METRIC3)){
                    lengthyTestCount += calculateLengthyTestsByPrev(cr, allTests);
                }
                else{
                    float alpha = 0.5f;
                    lengthyTestCount += calculateLengthyTestsByMean(alpha, cr, allTests);
                }
            }
            NumberOnlyBuildLabel label = new NumberOnlyBuildLabel(a.run);
            dsb.add(lengthyTestCount, "Lengthy Tests", label);
            /**
             * Also being stored in {@link #lengthyToolTip} in order to generate tooltips on hovering mouse
             * over the trend.
             */
            lengthyToolTip.put(label,lengthyTestCount);
            stack.pop();
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
        return dsb.build();
    }

    /**
     * A utility method to shift the sliding window, being used for determining flaky behaviour of a
     * testcase based upon whether a failed testcase passed in any of the builds in sliding window. If true
     * the testcase is flaky else it is not.
     * @param q The sliding window which is being implemented as queue containing objects of type
     *          {@link AbstractTestResultAction}.
     * @param flakySet All the testcases which passed in the previous builds of sliding window. The failed
     *                 testcase whose behaviour needs to be determined is looked up in this set and if it's
     *                 there then it is flaky else it is not.
     * @param dsb A dataset used for generating trend of no. of flaky testcases in a build.
     * @param projectLevel The particular chosen project or all projects.
     * @param allPackages True if all projects need to be considered else false.
     */
    private void shiftWindowUtil(ArrayDeque<AbstractTestResultAction<?>> q,
                                 Map<String,Integer> flakySet,
                                 DataSetBuilder<String,NumberOnlyBuildLabel> dsb,
                                 String projectLevel, boolean allPackages){
        AbstractTestResultAction<?> a_ = q.peek();
        q.remove();
        hudson.tasks.junit.TestResult r_ = a_.loadXml();
        List<CaseResult> passedTests= r_.getPassedTests();
        for(CaseResult cr: passedTests){
            String testName = cr.getFullName();
            if(!allPackages&&!testName.startsWith(projectLevel)) continue;
            flakySet.put(testName,flakySet.get(testName)-1);
            if(flakySet.get(testName)==0) flakySet.remove(testName);
        }
        List<CaseResult> failedTests = r_.getFailedTests();
        int flakeCount = 0;
        String toolTipString = "";
        for(CaseResult cr: failedTests){
            String testName = cr.getFullName();
            if(!allPackages&&!testName.startsWith(projectLevel)) continue;
            if(flakySet.containsKey(testName)){
                flakeCount++;
                if(!toolTipString.equals("")) toolTipString+=',';
                toolTipString+=cr.getName();
            }
        }
        NumberOnlyBuildLabel label = new NumberOnlyBuildLabel(a_.run);
        dsb.add(flakeCount,"Flaky Tests",label);
        /**
         * Also being stored in {@link #flapperToolTip} in order to generate tooltips on hovering mouse over the
         * trend.
         */
        flapperToolTip.put(label,toolTipString);
    }

    /**
     * A method to build dataset for the chosen project in order to generate trends for the analysis of
     * flaky testcases.
     * @param req The HTTP request message for the particular project or all projects for flaky tests
     *            trend type.
     * @return An object of type {@link CategoryDataset} in which columns are the build numbers to be
     * displayed on x-axis and row is the data series depicting no. of failed testcases which are flaky
     * for the respective builds.
     * <p>
     * This method creates {@link CategoryDataset} object for the chosen project with data series named
     * "Flaky Tests".
     */
    private CategoryDataset buildFlakyTestDataset(StaplerRequest req){
        String projectLevel = getParameter(req,PROJECTLEVEL);
        boolean allPackages = projectLevel.equals(ALLPROJECTS);
        DataSetBuilder<String,NumberOnlyBuildLabel> dsb = new DataSetBuilder<String,NumberOnlyBuildLabel>();
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        int flakyTestWindow = 10;
        /**
         * A map storing mapping between a testcase and no. of times it passed in the previous builds
         * contained in the sliding window.
         * <p>
         * Used for determining if a failed testcase is flaky or not by examining if its corresponding
         * value in this map > 0. If yes it is flaky else it is not.
         */
        Map<String,Integer> flakySet = new HashMap<String,Integer>();
        /**
         * The sliding window implemented as queue and contains object of type
         * {@link AbstractTestResultAction}.
         */
        ArrayDeque<AbstractTestResultAction<?>> q = new ArrayDeque<AbstractTestResultAction<?>>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[] {run, cap});
                break;
            }
            if(count>flakyTestWindow){
                shiftWindowUtil(q,flakySet,dsb,projectLevel,allPackages);
            }
            hudson.tasks.junit.TestResult r = a.loadXml();
            List<CaseResult> passedTests = r.getPassedTests();
            q.add(a);
            for(CaseResult cr: passedTests){
                String testName = cr.getFullName();
                if(!allPackages&&!testName.startsWith(projectLevel)) continue;
                if(!flakySet.containsKey(testName)){
                    flakySet.put(testName,0);
                }
                flakySet.put(testName,flakySet.get(testName)+1);
            }
        }
        while(!q.isEmpty()){
            shiftWindowUtil(q,flakySet,dsb,projectLevel,allPackages);
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
        return dsb.build();
    }

    /**
     * A utility method for shifting the larger window involved in determining whether the given testcase
     * is a test flapper or not.
     * @param q2 The larger window is implemented as queue and contains object of type
     *           {@link AbstractTestResultAction}.
     * @param testMap A mapping from the testcase to the queue of build indices where testcase is the key and
     *                its corresponding value is the queue of build indices at which the testcase failed or
     *                passed consecutively.
     * @param dsb A dataset used for generating trend of no. of flaky testcases in a build.
     * @param projectLevel The particular chosen project or all projects.
     * @param allPackages True if all projects need to be considered else false.
     * @param flapperTestWindow Size of larger window.
     * @param certaintyCount Size of smaller window. If at any point within larger window this window
     *                       contains only passed or failed instances of a given testcase, then that
     *                       testcase is not examined anymore in the larger window.
     * @param count_ count of no. of steps the larger window has shifted.
     * @param count Total count of no. of builds examined. It can be considered as 1-based index of a build.
     * @param emptying True if all the builds have been examined by larger window and larger window is being
     *                 emptied.
     * <p>
     * As soon as we reach end of larger window or smaller window contains all passed or failed instances
     * of a testcase, testcase is declared as test flapper if:
     *                 no. of steps smaller window moved > (flapperTestWindow-certaintyCount+1)/2
     */
    private void shiftLargerWindowUtil(ArrayDeque<AbstractTestResultAction<?>> q2,
                                       Map<String, ArrayDeque<Integer>> testMap,
                                       DataSetBuilder<String,NumberOnlyBuildLabel> dsb,
                                       String projectLevel, boolean allPackages,
                                       int flapperTestWindow, int certaintyCount,
                                       int count_, int count, boolean emptying){
        AbstractTestResultAction<?> a_ = q2.peek();
        q2.remove();
        hudson.tasks.junit.TestResult r_ = a_.loadXml();
        List<CaseResult> tests = r_.getFailedTests();
        int failFlap =0, passFlap = 0;
        /**
         * Variable to hold string to be displayed as tooltip i.e. the string displayed when we hover mouse
         * over the trend.
         */
        String toolTipString = "";
        for(CaseResult cr: tests){
            String caseName = cr.getFullName();
            if(!allPackages&&!caseName.startsWith(projectLevel)) continue;
            /**
             * A queue of build indices where smaller window contained all failed or passed instances of
             * the testcase.
             */
            ArrayDeque<Integer> q = testMap.getOrDefault(caseName,null);
            while(q!=null&&!q.isEmpty()&&q.peek()-count_<certaintyCount-1){
                q.remove();
            }
            if(q!=null&&!q.isEmpty()){
                int flapCount = Math.min(flapperTestWindow,q.peek()-count_)-certaintyCount+1;
                int threshCount = (flapperTestWindow-certaintyCount+1)/2;
                if(flapCount> threshCount){
                    failFlap++;
                    if(!toolTipString.equals("")) toolTipString+=',';
                    toolTipString+=cr.getName();
                }
            }
            else{
                int flapCount = Math.min(flapperTestWindow,count+1-count_)-certaintyCount+1;
                int threshCount = (flapperTestWindow-certaintyCount+1)/2;
                if(!emptying||flapCount> threshCount){
                    failFlap++;
                    if(!toolTipString.equals("")) toolTipString+=',';
                    toolTipString+=cr.getName();
                }
            }
        }
        tests = r_.getPassedTests();
        for(CaseResult cr: tests){
            String caseName = cr.getFullName();
            if(!allPackages&&!caseName.startsWith(projectLevel)) continue;
            /**
             * A queue of build indices where smaller window contained all failed or passed instances of
             * the testcase.
             */
            ArrayDeque<Integer> q = testMap.getOrDefault(caseName,null);
            while(q!=null&&!q.isEmpty()&&q.peek()-count_<certaintyCount-1){
                q.remove();
            }
            if(q!=null&&!q.isEmpty()){
                int flapCount = Math.min(flapperTestWindow,q.peek()-count_)-certaintyCount+1;
                int threshCount = (flapperTestWindow-certaintyCount+1)/2;
                if(flapCount> threshCount){
                    passFlap++;
                    if(!toolTipString.equals("")) toolTipString+=',';
                    toolTipString+=cr.getName();
                }
            }
            else{
                int flapCount = Math.min(flapperTestWindow,count+1-count_)-certaintyCount+1;
                int threshCount = (flapperTestWindow-certaintyCount+1)/2;
                if(!emptying||flapCount> threshCount){
                    passFlap++;
                    if(!toolTipString.equals("")) toolTipString+=',';
                    toolTipString+=cr.getName();
                }
            }
        }
        NumberOnlyBuildLabel label = new NumberOnlyBuildLabel(a_.run);
        dsb.add(passFlap+failFlap,"Test Flappers",label);
        /**
         * Also being stored in {@link #flapperToolTip} in order to generate tooltips on hovering mouse over the
         * trend.
         */
        flapperToolTip.put(label,toolTipString);
    }

    /**
     * A utility method for shifting the smaller window involved in determining whether the given testcase
     * is a test flapper or not.
     * @param q1 The smaller window is implemented as queue and contains object of type
     *           {@link AbstractTestResultAction}.
     * @param failedCount A mapping from testcase to no. of failed instances of that testcase in smaller
     *                    window.
     * @param passedCount A mapping from testcase to no. of passed instances of that testcase in smaller
     *                    window.
     * @param projectLevel The particular chosen project or all projects.
     * @param allPackages True if all projects need to be considered else false.
     */
    private void shiftSmallerWindowUtil(ArrayDeque<AbstractTestResultAction<?>> q1,
                                        Map<String, Integer> failedCount,
                                        Map<String, Integer> passedCount,
                                        String projectLevel, boolean allPackages){
        AbstractTestResultAction<?> a_ = q1.peek();
        q1.remove();
        hudson.tasks.junit.TestResult r_ = a_.loadXml();
        List<CaseResult> tests = r_.getFailedTests();
        for(CaseResult cr: tests){
            String caseName = cr.getFullName();
            if(!allPackages&&!caseName.startsWith(projectLevel)) continue;
            failedCount.put(caseName, failedCount.get(caseName)-1);
            if(failedCount.get(caseName)==0){
                failedCount.remove(caseName);
            }
        }
        tests = r_.getPassedTests();
        for(CaseResult cr: tests){
            String caseName = cr.getFullName();
            if(!allPackages&&!caseName.startsWith(projectLevel)) continue;
            passedCount.put(caseName, passedCount.get(caseName)-1);
            if(passedCount.get(caseName)==0){
                passedCount.remove(caseName);
            }
        }
    }

    /**
     * A method to build dataset for the chosen project in order to generate trends for the analysis of
     * test flappers.
     * @param req The HTTP request message for the particular project or all projects for flaky tests
     *            trend type.
     * @return An object of type {@link CategoryDataset} in which columns are the build numbers to be
     * displayed on x-axis and row is the data series depicting no. of testcases which are test flappers
     * for the respective builds.
     * <p>
     * This method creates {@link CategoryDataset} object for the chosen project with data series named
     * "Test Flappers".
     */
    private CategoryDataset buildFlapperTestDataset(StaplerRequest req){
        String projectLevel = getParameter(req,PROJECTLEVEL);
        boolean allPackages = projectLevel.equals(ALLPROJECTS);
        DataSetBuilder<String,NumberOnlyBuildLabel> dsb = new DataSetBuilder<String,NumberOnlyBuildLabel>();
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0, count_ = 0;
        int flapperTestWindow = 10;
        int certaintyCount = 3;
        certaintyCount = Math.min(flapperTestWindow,certaintyCount);
        /**
         * A mapping from the testcase to the queue of build indices where testcase is the key and
         * its corresponding value is the queue of build indices at which the testcase failed or
         * passed consecutively.
         */
        Map<String, ArrayDeque<Integer>> testMap = new HashMap<String, ArrayDeque<Integer>>();
        /**
         * A mapping from testcase to no. of failed instances of that testcase in smaller
         * window.
         */
        Map<String, Integer> failedCount = new HashMap<String, Integer>();
        /**
         * A mapping from testcase to no. of passed instances of that testcase in smaller
         * window.
         */
        Map<String, Integer> passedCount = new HashMap<String, Integer>();
        /**
         * The smaller window is implemented as queue and contains object of type
         * {@link AbstractTestResultAction}.
         */
        ArrayDeque<AbstractTestResultAction<?>> q1 = new ArrayDeque<AbstractTestResultAction<?>>();
        /**
         * The larger window is implemented as queue and contains object of type
         * {@link AbstractTestResultAction}.
         */
        ArrayDeque<AbstractTestResultAction<?>> q2 = new ArrayDeque<AbstractTestResultAction<?>>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[] {run, cap});
                break;
            }
            if(count>flapperTestWindow){
                count_++;
                shiftLargerWindowUtil(q2,testMap,dsb,projectLevel,allPackages,flapperTestWindow,certaintyCount,
                        count_,count,false);
            }
            if(count>certaintyCount){
                shiftSmallerWindowUtil(q1, failedCount, passedCount, projectLevel, allPackages);
            }
            q1.add(a);
            q2.add(a);
            hudson.tasks.junit.TestResult r = a.loadXml();
            List<CaseResult> tests = r.getFailedTests();
            for(CaseResult cr: tests){
                String caseName = cr.getFullName();
                if(!allPackages&&!caseName.startsWith(projectLevel)) continue;
                if(!failedCount.containsKey(caseName)){
                    failedCount.put(caseName,0);
                }
                failedCount.put(caseName,failedCount.get(caseName)+1);
                if(failedCount.get(caseName)==certaintyCount){
                    if(!testMap.containsKey(caseName)){
                        testMap.put(caseName, new ArrayDeque<Integer>());
                    }
                    testMap.get(caseName).add(count);
                }
            }
            tests = r.getPassedTests();
            for(CaseResult cr: tests){
                String caseName = cr.getFullName();
                if(!allPackages&&!caseName.startsWith(projectLevel)) continue;
                if(!passedCount.containsKey(caseName)){
                    passedCount.put(caseName,0);
                }
                passedCount.put(caseName, passedCount.get(caseName)+1);
                if(passedCount.get(caseName)==certaintyCount){
                    if(!testMap.containsKey(caseName)){
                        testMap.put(caseName, new ArrayDeque<Integer>());
                    }
                    testMap.get(caseName).add(count);
                }
            }
        }
        q1.clear();
        passedCount.clear();
        failedCount.clear();
        while(!q2.isEmpty()){
            count_++;
            shiftLargerWindowUtil(q2,testMap,dsb,projectLevel,allPackages,flapperTestWindow,certaintyCount,
                    count_,count,true);
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
        return dsb.build();
    }

    /**
     * A method to get y-axis/range axis label for the trend.
     * @param req The HTTP request message.
     * @return Y-axis/range axis label as a string.
     */
    private String getYAxisLabel(StaplerRequest req){
        String trendType = getParameter(req,TRENDTYPE);
        if(trendType.equals(TREND1)||trendType.equals(TREND2)||trendType.equals(TREND3)) return "Count";
        else return "count";
    }

    /**
     * Method to create and render trends on Jenkins UI.
     * @param req The HTTP request message.
     * @param dataset The dataset containing each of the data series to be rendered on the generated chart.
     * @return An object of type {@link JFreeChart} which contains information about all the properties of
     * chart as well as the renderer object.
     * <p>
     * The renderer object has overridden {@link StackedAreaRenderer2#generateURL(CategoryDataset, int, int)}
     * and {@link StackedAreaRenderer2#generateToolTip(CategoryDataset, int, int)} for generating custom url
     * for clickable map and for generating custom tool tip to display on hovering mouse over the chart
     * respectively.
     */
    private JFreeChart createChart(StaplerRequest req,CategoryDataset dataset) {

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

        /**
         * "Messages" is present inside resources/hudson/tasks/test/Resource Bundle 'Messages' as properties
         * file and "Messages" class get constructed on building the plugin inside target/generated-sources/
         * localizer/hudson/tasks/test.
         * <p>
         * The Messages class is used for the purpose of internationalization.
         */
        if(getParameter(req,TRENDTYPE).equals(TREND1)){
            ar = new StackedAreaRenderer2() {
                @Override
                public String generateURL(CategoryDataset dataset, int row, int column) {
                    NumberOnlyBuildLabel label = (NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    return relPath+label.getRun().getNumber()+"/testReport/";
                }

                @Override
                public String generateToolTip(CategoryDataset dataset, int row, int column) {
                    NumberOnlyBuildLabel label = (NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    switch (row) {
                        case 0:
                            return String.valueOf(Messages.AbstractTestResultAction_fail(label.getRun().getDisplayName(), failToolTip.get(label)));
                        case 1:
                            return String.valueOf(Messages.AbstractTestResultAction_skip(label.getRun().getDisplayName(), skipToolTip.get(label)));
                        default:
                            return String.valueOf(Messages.AbstractTestResultAction_test(label.getRun().getDisplayName(), totalToolTip.get(label)));
                    }
                }
            };
        }
        else if(getParameter(req,TRENDTYPE).equals(TREND2)){
            ar = new StackedAreaRenderer2() {
                @Override
                public String generateURL(CategoryDataset dataset, int row, int column) {
                    NumberOnlyBuildLabel label = (NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    return relPath+label.getRun().getNumber()+"/testReport/";
                }

                @Override
                public String generateToolTip(CategoryDataset dataset, int row, int column) {
                    NumberOnlyBuildLabel label = (NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    return String.valueOf(Messages.AbstractTestResultAction_lengthyTests(label.getRun().getDisplayName(), lengthyToolTip.get(label)));
                }
            };
        }
        else if(getParameter(req,TRENDTYPE).equals(TREND3)){
            ar = new StackedAreaRenderer2() {
                @Override
                public String generateURL(CategoryDataset dataset, int row, int column) {
                    NumberOnlyBuildLabel label = (NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    return relPath+label.getRun().getNumber()+"/testReport/";
                }

                @Override
                public String generateToolTip(CategoryDataset dataset, int row, int column) {
                    NumberOnlyBuildLabel label = (NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    return String.valueOf(Messages.AbstractTestResultAction_flakyTests(label.getRun().getDisplayName(), flapperToolTip.get(label)));
                }
            };
        }
        else{
            ar = new StackedAreaRenderer2() {
                @Override
                public String generateURL(CategoryDataset dataset, int row, int column) {
                    NumberOnlyBuildLabel label = (NumberOnlyBuildLabel) dataset.getColumnKey(column);
                    return relPath+label.getRun().getNumber()+"/testReport/";
                }

                @Override
                public String generateToolTip(CategoryDataset dataset, int row, int column) {
                    NumberOnlyBuildLabel label = (NumberOnlyBuildLabel) dataset.getColumnKey(column);
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
        ar.setSeriesPaint(0,ColorPalette.RED); // First data series.
        ar.setSeriesPaint(1,ColorPalette.YELLOW); // Second data series.
        ar.setSeriesPaint(2,ColorPalette.BLUE); // third data series.

        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0,0,0,5.0));

        return chart;
    }

    private String getRelPath(StaplerRequest req) {
        String relPath = req.getParameter("rel");
        if(relPath==null)   return "";
        return relPath;
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

    @Extension public static final class Summarizer extends Run.StatusSummarizer {
        @Override public Run.Summary summarize(Run<?,?> run, ResultTrend trend) {
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
            } else {
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

    private static final String ISFAILUREONLY = "false";
    private static final String ALLPROJECTS = "AllProjects";
    private static final String TREND1 = "BuildAnalysis";
    private static final String TREND2 = "LengthyTests";
    private static final String TREND3 = "FlakyTests";
    private static final String METRIC1 = "mean";
    private static final String METRIC2 = "max";
    private static final String METRIC3 = "prev";
    private static final String METRIC4 = "threshold";
    private static final String FAILUREONLY = "failureOnly";
    private static final String PROJECTLEVEL = "projectLevel";
    private static final String TRENDTYPE = "trendType";
    private static final String METRICNAME = "metricName";
}
