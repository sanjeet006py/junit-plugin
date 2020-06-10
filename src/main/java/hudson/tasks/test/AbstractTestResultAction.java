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

    private Map<NumberOnlyBuildLabel, Integer> map;

    private Map<NumberOnlyBuildLabel,String> toolTip;

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

    public hudson.tasks.junit.TestResult loadXmlUtil(){return null;}

    /**
     * A method for getting the list of packages for all levels of hierarchy.
     * @return Array of packages of all hierarchies.
     */
    public String[] getProjectList(){
        if(projectList!=null) return projectList;
        hudson.tasks.junit.TestResult r = loadXmlUtil();
        Collection<SuiteResult> suiteList = r.getSuites();
        Set<String> projectSet = new HashSet<String>(); //A set for the package names.
        for(SuiteResult sr: suiteList){
            for(CaseResult cr: sr.getCases()){
                String caseName = cr.getFullName();
                String projectName = "";
                String[] packageTree = caseName.split("[.]"); //Splitting package by treating "." as separator.
                for(int i=0;i<packageTree.length-2;i++){  //Iterating till the length-2 of packageTree array to exclude class name and test name.
                    if(!projectName.isEmpty()) projectName+='.';
                    projectName+=packageTree[i];
                    projectSet.add(projectName);
                }
            }
        }
        /*
        Converting the set of package names to the corresponding array and finally sorting it in ascending order.
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

        //ChartUtil.generateGraph(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
        //ChartUtil.generateGraph(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
        doGraphUtil(req, rsp);
    }

    /**
     * A utility method for constructing trends based upon the query parameters passed in the
     * request message.
     * @param req HTTP request message for the image of trend.
     * @param rsp HTTP respose message for the requested image.
     * @throws IOException
     */
    public void doGraphUtil(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String projectLevel = getParameter(req,PROJECTLEVEL);
        String trendType = getParameter(req,TRENDTYPE);
        /*
        A binary search
         */
        int indx = Arrays.binarySearch(projectList,projectLevel);
        if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND1)){
            ChartUtil.generateGraph(req,rsp,createChart(req,buildDataSetPerProject(req)),calcDefaultSize());
        }
        else if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND2)){
            if(map==null) map = new HashMap<NumberOnlyBuildLabel, Integer>();
            ChartUtil.generateGraph(req,rsp,createChart(req,buildLengthyTestDataset(req)),calcDefaultSize());
        }
        else if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND3)){
            if(toolTip==null) toolTip = new HashMap<NumberOnlyBuildLabel,String>();
            ChartUtil.generateGraph(req,rsp,createChart(req,buildFlapperTestDataset(req)),calcDefaultSize());
        }
        else{
            ChartUtil.generateGraph(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
        }
    }

    /**
     * Generates a clickable map HTML for {@link #doGraph(StaplerRequest, StaplerResponse)}.
     */
    public void doGraphMap( StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(req.checkIfModified(run.getTimestamp(),rsp))
            return;
        //ChartUtil.generateClickableMap(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
        doGraphMapUtil(req,rsp);
    }

    public void doGraphMapUtil(StaplerRequest req, StaplerResponse rsp) throws IOException{
        String projectLevel = getParameter(req,PROJECTLEVEL);
        String trendType = getParameter(req,TRENDTYPE);
        int indx = Arrays.binarySearch(projectList,projectLevel);
        if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND1)){
            ChartUtil.generateClickableMap(req,rsp,createChart(req,buildDataSetPerProject(req)),calcDefaultSize());
        }
        else if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND2)){
            if(map==null) map = new HashMap<NumberOnlyBuildLabel, Integer>();
            ChartUtil.generateClickableMap(req,rsp,createChart(req,buildLengthyTestDataset(req)),calcDefaultSize());
        }
        else if((indx>=0||projectLevel.equals(ALLPROJECTS))&&trendType.equals(TREND3)){
            if(toolTip==null) toolTip = new HashMap<NumberOnlyBuildLabel,String>();
            ChartUtil.generateClickableMap(req,rsp,createChart(req,buildFlapperTestDataset(req)),calcDefaultSize());
        }
        else{
            ChartUtil.generateClickableMap(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
        }
    }

    private String getParameter(StaplerRequest req, String paramName){
        String paramValue = req.getParameter(paramName);
        if(paramValue==null){
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

    private CategoryDataset buildDataSetPerProject(StaplerRequest req){
        //System.out.println(Thread.currentThread().getName());
        //System.out.println(Thread.currentThread().getId());
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
            hudson.tasks.junit.TestResult r = a.loadXmlUtil();
            List<CaseResult> passedTests = r.getPassedTests();
            List<CaseResult> failedTests = r.getFailedTests();
            List<CaseResult> skippedTests = r.getSkippedTests();
            int failCount = 0, passCount = 0, skipCount = 0;
            for(CaseResult cr: failedTests){
                String caseName = cr.getFullName();
                if(allPackages||(!allPackages&&caseName.startsWith(projectLevel))){
                    failCount++;
                }
            }
            dsb.add(failCount, "failed", new NumberOnlyBuildLabel(a.run));
            if(!failureOnly) {
                for(CaseResult cr: passedTests){
                    String caseName = cr.getFullName();
                    if(allPackages||(!allPackages&&caseName.startsWith(projectLevel))){
                        passCount++;
                    }
                }
                for(CaseResult cr: skippedTests){
                    String caseName = cr.getFullName();
                    if(allPackages||(!allPackages&&caseName.startsWith(projectLevel))){
                        skipCount++;
                    }
                }
                dsb.add( skipCount, "skipped", new NumberOnlyBuildLabel(a.run));
                dsb.add( passCount,"total", new NumberOnlyBuildLabel(a.run));
            }
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
        return dsb.build();
    }

    private void calculateLengthyTestsByMean(float alpha, String projectName){
        //System.out.println(Thread.currentThread().getName());
        //System.out.println(Thread.currentThread().getId());
        boolean allPackages = projectName.equals(ALLPROJECTS);
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        Deque<AbstractTestResultAction> stack = new ArrayDeque<AbstractTestResultAction>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[]{run, cap});
                break;
            }
            stack.push(a);
        }
        Map<String,Float> allTests = new HashMap<String,Float>();
        while(!stack.isEmpty()){
            AbstractTestResultAction a = stack.peek();
            hudson.tasks.junit.TestResult r = a.loadXmlUtil();
            List<CaseResult> passedTests = r.getPassedTests();
            int lengthyTestCount = 0;
            for(CaseResult cr: passedTests){
                if(!allPackages&&!cr.getFullName().startsWith(projectName)) continue;
                String testName = cr.getFullName();
                float ewmaTime =  allTests.getOrDefault(testName,0.0f);
                if(ewmaTime!=0.0f&&cr.getDuration()>ewmaTime){
                    lengthyTestCount++;
                }
                if(ewmaTime==0.0f){
                    allTests.put(testName,cr.getDuration());
                }
                else {
                    ewmaTime = alpha * cr.getDuration() + (1 - alpha) * ewmaTime;
                    ewmaTime = (1.0f*Math.round(100000*ewmaTime))/100000;
                    allTests.put(testName,ewmaTime);
                }
            }
            map.put(new NumberOnlyBuildLabel(a.run),lengthyTestCount);
            stack.pop();
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
    }

    private void calculateLengthyTestsByMax(String projectName){
        boolean allPackages = projectName.equals(ALLPROJECTS);
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        Deque<AbstractTestResultAction> stack = new ArrayDeque<AbstractTestResultAction>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[]{run, cap});
                break;
            }
            stack.push(a);
        }
        Map<String,Float> allTests = new HashMap<String,Float>();
        while(!stack.isEmpty()){
            AbstractTestResultAction a = stack.peek();
            hudson.tasks.junit.TestResult r = a.loadXmlUtil();
            List<CaseResult> passedTests = r.getPassedTests();
            int lengthyTestCount = 0;
            for(CaseResult cr: passedTests){
                if(!allPackages&&!cr.getFullName().startsWith(projectName)) continue;
                String testName = cr.getFullName();
                float maxTime =  allTests.getOrDefault(testName,0.0f);
                if(maxTime!=0.0f&&cr.getDuration()>maxTime){
                    lengthyTestCount++;
                }
                maxTime = Math.max(maxTime,cr.getDuration());
                allTests.put(testName,maxTime);
            }
            map.put(new NumberOnlyBuildLabel(a.run),lengthyTestCount);
            stack.pop();
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
    }

    private void calculateLengthyTestsByPrev(String projectName){
        boolean allPackages = projectName.equals(ALLPROJECTS);
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        Deque<AbstractTestResultAction> stack = new ArrayDeque<AbstractTestResultAction>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[]{run, cap});
                break;
            }
            stack.push(a);
        }
        Map<String,Float> allTests = new HashMap<String,Float>();
        while(!stack.isEmpty()){
            AbstractTestResultAction a = stack.peek();
            hudson.tasks.junit.TestResult r = a.loadXmlUtil();
            List<CaseResult> passedTests = r.getPassedTests();
            int lengthyTestCount = 0;
            for(CaseResult cr: passedTests){
                if(!allPackages&&!cr.getFullName().startsWith(projectName)) continue;
                String testName = cr.getFullName();
                float prevTime =  allTests.getOrDefault(testName,0.0f);
                if(prevTime!=0.0f&&cr.getDuration()>prevTime){
                    lengthyTestCount++;
                }
                prevTime = cr.getDuration();
                allTests.put(testName,prevTime);
            }
            map.put(new NumberOnlyBuildLabel(a.run),lengthyTestCount);
            stack.pop();
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
    }

    private void calculateLengthyTestsByThreshold(float threshold, String projectName){
        boolean allPackages = projectName.equals(ALLPROJECTS);
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        Deque<AbstractTestResultAction> stack = new ArrayDeque<AbstractTestResultAction>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[]{run, cap});
                break;
            }
            stack.push(a);
        }
        Set<String> allTests = new HashSet<String>();
        while(!stack.isEmpty()){
            AbstractTestResultAction a = stack.peek();
            hudson.tasks.junit.TestResult r = a.loadXmlUtil();
            List<CaseResult> passedTests = r.getPassedTests();
            int lengthyTestCount = 0;
            for(CaseResult cr: passedTests){
                if(!allPackages&&!cr.getFullName().startsWith(projectName)) continue;
                String testName = cr.getFullName();
                if(allTests.contains(testName)&&cr.getDuration()>threshold){
                    lengthyTestCount++;
                }
                allTests.add(testName);
            }
            map.put(new NumberOnlyBuildLabel(a.run),lengthyTestCount);
            stack.pop();
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
    }

    private CategoryDataset buildLengthyTestDataset(StaplerRequest req){
        //System.out.println(Thread.currentThread().getName());
        //System.out.println(Thread.currentThread().getId());
        String projectLevel = getParameter(req,PROJECTLEVEL);
        String metricName = getParameter(req,METRICNAME);
        DataSetBuilder<String,NumberOnlyBuildLabel> dsb = new DataSetBuilder<String,NumberOnlyBuildLabel>();
        if(metricName.equals(METRIC4)) {
            float threshold = 0.002f;
            calculateLengthyTestsByThreshold(threshold,projectLevel);
        }
        else if(metricName.equals(METRIC2)){
            calculateLengthyTestsByMax(projectLevel);
        }
        else if(metricName.equals(METRIC3)){
            calculateLengthyTestsByPrev(projectLevel);
        }
        else{
            float alpha = 0.5f;
            calculateLengthyTestsByMean(alpha,projectLevel);
        }
        for(NumberOnlyBuildLabel label: map.keySet()) {
            dsb.add(map.get(label), "Lengthy Tests", label);
        }
        return dsb.build();
    }

    private CategoryDataset buildFlakyTestDataset(StaplerRequest req){
        //System.out.println(Thread.currentThread().getName());
        //System.out.println(Thread.currentThread().getId());
        String projectLevel = getParameter(req,PROJECTLEVEL);
        boolean allPackages = projectLevel.equals(ALLPROJECTS);
        DataSetBuilder<String,NumberOnlyBuildLabel> dsb = new DataSetBuilder<String,NumberOnlyBuildLabel>();
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        int flakyTestWindow = 10;
        flakyTestWindow = Math.min(cap,flakyTestWindow);
        Map<String,Integer> flakySet = new HashMap<String,Integer>();
        ArrayDeque<AbstractTestResultAction<?>> q = new ArrayDeque<AbstractTestResultAction<?>>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[] {run, cap});
                break;
            }
            if(count>flakyTestWindow){
                AbstractTestResultAction<?> a_ = q.peek();
                q.remove();
                hudson.tasks.junit.TestResult r_ = a_.loadXmlUtil();
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
                toolTip.put(label,toolTipString);
            }
            hudson.tasks.junit.TestResult r = a.loadXmlUtil();
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
            AbstractTestResultAction<?> a_ = q.peek();
            q.remove();
            hudson.tasks.junit.TestResult r_ = a_.loadXmlUtil();
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
            toolTip.put(label,toolTipString);
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
        return dsb.build();
    }

    private void shiftLargerWindowUtil(ArrayDeque<AbstractTestResultAction<?>> q2,
                                       Map<String, ArrayDeque<Integer>> testMap,
                                       DataSetBuilder<String,NumberOnlyBuildLabel> dsb,
                                       String projectLevel, boolean allPackages,
                                       int flapperTestWindow, int certainityCount,
                                       int count_, int count, boolean emptying){
        AbstractTestResultAction<?> a_ = q2.peek();
        q2.remove();
        hudson.tasks.junit.TestResult r_ = a_.loadXmlUtil();
        List<CaseResult> tests = r_.getFailedTests();
        int failFlap =0, passFlap = 0;
        String toolTipString = "";
        for(CaseResult cr: tests){
            String caseName = cr.getFullName();
            if(!allPackages&&!caseName.startsWith(projectLevel)) continue;
            ArrayDeque<Integer> q = testMap.getOrDefault(caseName,null);
            while(q!=null&&!q.isEmpty()&&q.peek()-count_<certainityCount-1){
                q.remove();
            }
            if(q!=null&&!q.isEmpty()){
                int flapCount = Math.min(flapperTestWindow,q.peek()-count_)-certainityCount+1;
                int threshCount = (flapperTestWindow-certainityCount+1)/2;
                if(flapCount> threshCount){
                    failFlap++;
                    if(!toolTipString.equals("")) toolTipString+=',';
                    toolTipString+=cr.getName();
                }
            }
            else{
                int flapCount = Math.min(flapperTestWindow,count+1-count_)-certainityCount+1;
                int threshCount = (flapperTestWindow-certainityCount+1)/2;
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
            ArrayDeque<Integer> q = testMap.getOrDefault(caseName,null);
            while(q!=null&&!q.isEmpty()&&q.peek()-count_<certainityCount-1){
                q.remove();
            }
            if(q!=null&&!q.isEmpty()){
                int flapCount = Math.min(flapperTestWindow,q.peek()-count_)-certainityCount+1;
                int thershCount = (flapperTestWindow-certainityCount+1)/2;
                if(flapCount>thershCount){
                    passFlap++;
                    if(!toolTipString.equals("")) toolTipString+=',';
                    toolTipString+=cr.getName();
                }
            }
            else{
                int flapCount = Math.min(flapperTestWindow,count+1-count_)-certainityCount+1;
                int thershCount = (flapperTestWindow-certainityCount+1)/2;
                if(!emptying||flapCount>thershCount){
                    passFlap++;
                    if(!toolTipString.equals("")) toolTipString+=',';
                    toolTipString+=cr.getName();
                }
            }
        }
        NumberOnlyBuildLabel label = new NumberOnlyBuildLabel(a_.run);
        dsb.add(passFlap+failFlap,"Test Flappers",label);
        toolTip.put(label,toolTipString);
    }

    private CategoryDataset buildFlapperTestDataset(StaplerRequest req){
        String projectLevel = getParameter(req,PROJECTLEVEL);
        boolean allPackages = projectLevel.equals(ALLPROJECTS);
        DataSetBuilder<String,NumberOnlyBuildLabel> dsb = new DataSetBuilder<String,NumberOnlyBuildLabel>();
        int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0, count_ = 0;
        int flapperTestWindow = 10;
        int certainityCount = 3;
        //flapperTestWindow = Math.min(cap,flapperTestWindow);
        certainityCount = Math.min(flapperTestWindow,certainityCount);
        Map<String, ArrayDeque<Integer>> testMap = new HashMap<String, ArrayDeque<Integer>>();
        Map<String, Integer> failedCount = new HashMap<String, Integer>();
        Map<String, Integer> passedCount = new HashMap<String, Integer>();
        ArrayDeque<AbstractTestResultAction<?>> q1 = new ArrayDeque<AbstractTestResultAction<?>>();
        ArrayDeque<AbstractTestResultAction<?>> q2 = new ArrayDeque<AbstractTestResultAction<?>>();
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[] {run, cap});
                break;
            }
            if(count>flapperTestWindow){
                count_++;
                shiftLargerWindowUtil(q2,testMap,dsb,projectLevel,allPackages,flapperTestWindow,certainityCount,
                        count_,count,false);
            }
            if(count>certainityCount){
                AbstractTestResultAction<?> a_ = q1.peek();
                q1.remove();
                hudson.tasks.junit.TestResult r_ = a_.loadXmlUtil();
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
            q1.add(a);
            q2.add(a);
            hudson.tasks.junit.TestResult r = a.loadXmlUtil();
            List<CaseResult> tests = r.getFailedTests();
            for(CaseResult cr: tests){
                String caseName = cr.getFullName();
                if(!allPackages&&!caseName.startsWith(projectLevel)) continue;
                if(!failedCount.containsKey(caseName)){
                    failedCount.put(caseName,0);
                }
                failedCount.put(caseName,failedCount.get(caseName)+1);
                if(failedCount.get(caseName)==certainityCount){
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
                if(passedCount.get(caseName)==certainityCount){
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
            shiftLargerWindowUtil(q2,testMap,dsb,projectLevel,allPackages,flapperTestWindow,certainityCount,
                    count_,count,true);
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[] {run, count});
        return dsb.build();
    }

    private String getYAxisLabel(StaplerRequest req){
        String trendType = getParameter(req,TRENDTYPE);
        if(trendType.equals(TREND1)||trendType.equals(TREND2)||trendType.equals(TREND3)) return "Count";
        else return "count";
    }

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

        if(getParameter(req,TRENDTYPE).equals(TREND2)){
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
                    return String.valueOf(Messages.AbstractTestResultAction_lengthyTests(label.getRun().getDisplayName(),map.get(label)));
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
                    AbstractTestResultAction a = label.getRun().getAction(AbstractTestResultAction.class);
                    return String.valueOf(Messages.AbstractTestResultAction_flakyTests(label.getRun().getDisplayName(),toolTip.get(label)));
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
        ar.setSeriesPaint(0,ColorPalette.RED); // Failures.
        ar.setSeriesPaint(1,ColorPalette.YELLOW); // Skips.
        ar.setSeriesPaint(2,ColorPalette.BLUE); // Total.

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
