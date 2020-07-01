package hudson.tasks.test;

import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.kohsuke.stapler.StaplerRequest;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.api.support.membermodification.MemberMatcher;
import org.powermock.api.support.membermodification.MemberModifier;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


@RunWith(PowerMockRunner.class)
@PrepareForTest({TestResult.class, SuiteResult.class, TestResultAction.class, AbstractTestResultAction.class, Integer.class, Logger.class, FreeStyleBuild.class})
public class AbstractTestResultActionTest {

    private static Logger LOGGER;

    @BeforeClass
    public static void setUp(){
        LOGGER = PowerMockito.mock(Logger.class);
        PowerMockito.doNothing().when(LOGGER).log(Level.FINER,"",new Object[]{1,1});
        PowerMockito.mockStatic(Logger.class);
        PowerMockito.when(Logger.getLogger(AbstractTestResultAction.class.getName())).thenReturn(LOGGER);
    }

    /**
     * Testcase for testing the {@link AbstractTestResultAction#getProjectList()} which is responsible for generating
     * the drop down list showing all the project names.
     */
    @Test
    public void getProjectListTest () {

        TestResult testResult = PowerMockito.mock(TestResult.class);
        SuiteResult suiteResult1 = PowerMockito.mock(SuiteResult.class);
        SuiteResult suiteResult2 = PowerMockito.mock(SuiteResult.class);
        SuiteResult suiteResult3 = PowerMockito.mock(SuiteResult.class);
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = PowerMockito.spy(new TestResultAction(null, null, null));
        Collection<SuiteResult> suiteList = new ArrayList<>();
        suiteList.add(suiteResult1);
        suiteList.add(suiteResult2);
        suiteList.add(suiteResult3);
        PowerMockito.doReturn(testResult).when(abstractTestResultAction).loadXml();
        PowerMockito.doReturn(suiteList).when(testResult).getSuites();
        PowerMockito.doReturn("com.salesforce.hadoop.LoadTest").when(suiteResult1).getName();
        PowerMockito.doReturn("org.apache.hbase.QueryTest").when(suiteResult2).getName();
        PowerMockito.doReturn("com.salesforce.phoenix.UITest").when(suiteResult3).getName();
        String[] expectedProjectList = abstractTestResultAction.getProjectList();
        String[] actualProjectList = {"com.salesforce.hadoop", "com.salesforce.phoenix", "org.apache.hbase"};
        Assert.assertArrayEquals("Both arrays should be same", actualProjectList, expectedProjectList);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * is not null in the HTTP request header.
     */
    @Test
    public void getParametersWhenParamValueIsNotNull () throws Exception {

        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "projectLevel";
        String actualParamValue = "AllProjects";
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        Assert.assertEquals(actualParamValue, expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * for parameter "metricName" is null in the HTTP request header.
     */
    @Test
    public void getParametersWhenMetricNameIsNull () throws Exception {

        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "metricName";
        String actualParamValue = null;
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        Assert.assertEquals("mean", expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * for parameter "trendType" is null in the HTTP request header.
     */
    @Test
    public void getParametersWhenTrendTypeIsNull () throws Exception {

        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "trendType";
        String actualParamValue = null;
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        Assert.assertEquals("BuildAnalysis", expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * for parameter "projectLevel" is null in the HTTP request header.
     */
    @Test
    public void getParametersWhenProjectLevelIsNull () throws Exception {

        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "projectLevel";
        String actualParamValue = null;
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        Assert.assertEquals("AllProjects", expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * for parameter "orderBy" is null in the HTTP request header.
     */
    @Test
    public void getParametersWhenOrderByIsNull () throws Exception {

        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "orderBy";
        String actualParamValue = null;
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        Assert.assertEquals("fail", expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * for parameter "failureOnly" is null in the HTTP request header.
     */
    @Test
    public void getParametersWhenFailureOnlyIsNull () throws Exception {

        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "failureOnly";
        String actualParamValue = null;
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        Assert.assertEquals("false", expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#buildFlapperDataset(StaplerRequest)} when no testcase flapped
     * and the a particular project was chosen.
     */
    @Test
    public void buildFlapperDatasetTest () throws Exception {
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction1 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction2 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction3 = PowerMockito.spy(new TestResultAction(null, null, null));
        Run<?, ?> run = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run1 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run2 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run3 = Whitebox.newInstance(FreeStyleBuild.class);
        run.number = 4;
        run1.number = 3;
        run2.number = 2;
        run3.number = 1;
        Whitebox.setInternalState(abstractTestResultAction, "run", run);
        Whitebox.setInternalState(abstractTestResultAction1, "run", run1);
        Whitebox.setInternalState(abstractTestResultAction2, "run", run2);
        Whitebox.setInternalState(abstractTestResultAction3, "run", run3);
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        PowerMockito.doReturn("com").when(abstractTestResultAction, "getParameter", staplerRequest, "projectLevel");
        PowerMockito.doReturn("fail").when(abstractTestResultAction, "getParameter", staplerRequest, "orderBy");
        PowerMockito.spy(Integer.class);
        PowerMockito.when(Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE)).thenReturn(Integer.MAX_VALUE);
        PowerMockito.doReturn(abstractTestResultAction1).when(abstractTestResultAction).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction2).when(abstractTestResultAction1).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction3).when(abstractTestResultAction2).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(null).when(abstractTestResultAction3).getPreviousResult(AbstractTestResultAction.class);
        TestResult r = PowerMockito.mock(TestResult.class);
        CaseResult caseResult1 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult2 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult3 = PowerMockito.mock(CaseResult.class);
        List<CaseResult> caseResultList = new ArrayList<>();
        caseResultList.add(caseResult1);
        caseResultList.add(caseResult2);
        caseResultList.add(caseResult3);
        PowerMockito.doReturn(r).when(abstractTestResultAction).loadXml();
        PowerMockito.doReturn(r).when(abstractTestResultAction1).loadXml();
        PowerMockito.doReturn(r).when(abstractTestResultAction2).loadXml();
        PowerMockito.doReturn(r).when(abstractTestResultAction3).loadXml();
        PowerMockito.doReturn(caseResultList).when(r).getFailedTests();
        PowerMockito.doReturn("com.salesforce.hadoop.Class1.Test1").when(caseResult1).getFullName();
        PowerMockito.doReturn("org.apache.hbase.Class2.Test2").when(caseResult2).getFullName();
        PowerMockito.doReturn("com.salesforce.phoenix.Class3.Test3").when(caseResult3).getFullName();
        PowerMockito.doReturn(new ArrayList<>()).when(r).getPassedTests();
        XYSeriesCollection dataset = Whitebox.invokeMethod(abstractTestResultAction, "buildFlapperDataset", staplerRequest);
        XYSeriesCollection xySeriesCollection = new XYSeriesCollection();
        XYSeries xySeries;
        for (int series = 2; series >= 1; series--) {
            xySeries = new XYSeries(series);
            for (int index = 4; index >= 1; index--) {
                xySeries.add(index, series);
            }
            xySeriesCollection.addSeries(xySeries);
        }
        xySeries = new XYSeries(0);
        for (int index = 4; index >= 1; index--) {
            xySeries.add(index, null);
        }
        xySeries.add(4.5, 2.5);
        xySeriesCollection.addSeries(xySeries);
        Assert.assertEquals("collection size should be same", xySeriesCollection.getSeriesCount(), dataset.getSeriesCount());
        for (int series = 0; series < xySeriesCollection.getSeriesCount(); series++) {
            XYSeries expectedXYSeries = xySeriesCollection.getSeries(series);
            XYSeries actualXYSeries = dataset.getSeries(series);
            Assert.assertEquals("Series size should be same", expectedXYSeries.getItemCount(), actualXYSeries.getItemCount());
            for (int item = 0; item < expectedXYSeries.getItemCount(); item++) {
                Assert.assertEquals("X value should be same", expectedXYSeries.getX(item), actualXYSeries.getX(item));
                Assert.assertEquals("Y value should be same", expectedXYSeries.getY(item), actualXYSeries.getY(item));
            }
        }
    }
}