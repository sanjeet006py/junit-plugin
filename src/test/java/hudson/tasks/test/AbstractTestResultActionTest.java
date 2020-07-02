package hudson.tasks.test;

import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.util.ChartUtil;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mockito;
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


/**
 * Unit Tests for test result trends.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({TestResult.class, SuiteResult.class, TestResultAction.class, AbstractTestResultAction.class, Integer.class, Logger.class, FreeStyleBuild.class})
public class AbstractTestResultActionTest {

    private static Logger LOGGER;

    /**
     * Mocking the logger.
     */
    @BeforeClass
    public static void setUp () {
        LOGGER = PowerMockito.mock(Logger.class);
        PowerMockito.doNothing().when(LOGGER).log(Level.FINER, "", new Object[]{1, 1});
        PowerMockito.mockStatic(Logger.class);
        PowerMockito.when(Logger.getLogger(AbstractTestResultAction.class.getName())).thenReturn(LOGGER);
    }

    /**
     * Testcase for testing the {@link AbstractTestResultAction#getProjectList()} which is responsible for generating
     * the drop down list showing all the project names.
     */
    @Test
    public void getProjectListTest () {
        /*
        Mocking TestResult Object.
         */
        TestResult testResult = PowerMockito.mock(TestResult.class);
        /*
        Mocking SuiteResult object.
         */
        SuiteResult suiteResult1 = PowerMockito.mock(SuiteResult.class);
        SuiteResult suiteResult2 = PowerMockito.mock(SuiteResult.class);
        SuiteResult suiteResult3 = PowerMockito.mock(SuiteResult.class);
        /*
        Getting TestResultAction object as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = PowerMockito.spy(new TestResultAction(null, null, null));
        /*
        Making a list of test suites.
         */
        Collection<SuiteResult> suiteList = new ArrayList<>();
        suiteList.add(suiteResult1);
        suiteList.add(suiteResult2);
        suiteList.add(suiteResult3);
        /*
        Mocking loading of xml file.
         */
        PowerMockito.doReturn(testResult).when(abstractTestResultAction).loadXml();
        /*
        Mocking call to getSuites() to fetch the list of test suites.
         */
        PowerMockito.doReturn(suiteList).when(testResult).getSuites();
        /*
        Mocking the getName() method to fetch the name of test suite.
         */
        PowerMockito.doReturn("com.salesforce.hadoop.LoadTest").when(suiteResult1).getName();
        PowerMockito.doReturn("org.apache.hbase.QueryTest").when(suiteResult2).getName();
        PowerMockito.doReturn("com.salesforce.phoenix.UITest").when(suiteResult3).getName();
        /*
        Calling the getProjectList() method for testing.
         */
        String[] actualProjectList = abstractTestResultAction.getProjectList();
        /*
        Generating expected output.
         */
        String[] expectedProjectList = {"com.salesforce.hadoop", "com.salesforce.phoenix", "org.apache.hbase"};
        /*
        Verifying the output.
         */
        Assert.assertArrayEquals("Both arrays should be same", expectedProjectList, actualProjectList);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * is not null in the HTTP request header.
     */
    @Test
    public void getParametersWhenParamValueIsNotNull () throws Exception {
        /*
        Mocking StaplerRequest object.
         */
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "projectLevel";
        String actualParamValue = "AllProjects";
        /*
        Getting TestResultAction object as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        /*
        Mocking the getParameter() method of StaplerRequest class to fetch the value of query parameter.
         */
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        /*
        Calling the getParameter() method for testing.
         */
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        /*
        Verifying the output.
         */
        Assert.assertEquals(actualParamValue, expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * for parameter "metricName" is null in the HTTP request header.
     */
    @Test
    public void getParametersWhenMetricNameIsNull () throws Exception {
        /*
        Mocking StaplerRequest object.
         */
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "metricName";
        String actualParamValue = null;
        /*
        Getting TestResultAction object as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        /*
        Mocking the getParameter() method of StaplerRequest class to fetch the value of query parameter.
         */
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        /*
        Calling the getParameter() method for testing.
         */
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        /*
        Verifying the output.
         */
        Assert.assertEquals("mean", expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * for parameter "trendType" is null in the HTTP request header.
     */
    @Test
    public void getParametersWhenTrendTypeIsNull () throws Exception {
        /*
        Mocking StaplerRequest object.
         */
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "trendType";
        String actualParamValue = null;
        /*
        Getting TestResultAction object as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        /*
        Mocking the getParameter() method of StaplerRequest class to fetch the value of query parameter.
         */
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        /*
        Calling the getParameter() method for testing.
         */
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        /*
        Verifying the output.
         */
        Assert.assertEquals("BuildAnalysis", expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * for parameter "projectLevel" is null in the HTTP request header.
     */
    @Test
    public void getParametersWhenProjectLevelIsNull () throws Exception {
        /*
        Mocking StaplerRequest object.
         */
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "projectLevel";
        String actualParamValue = null;
        /*
        Getting TestResultAction object as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        /*
        Mocking the getParameter() method of StaplerRequest class to fetch the value of query parameter.
         */
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        /*
        Calling the getParameter() method for testing.
         */
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        /*
        Verifying the output.
         */
        Assert.assertEquals("AllProjects", expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * for parameter "orderBy" is null in the HTTP request header.
     */
    @Test
    public void getParametersWhenOrderByIsNull () throws Exception {
        /*
        Mocking StaplerRequest object.
         */
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "orderBy";
        String actualParamValue = null;
        /*
        Getting TestResultAction object as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        /*
        Mocking the getParameter() method of StaplerRequest class to fetch the value of query parameter.
         */
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        /*
        Calling the getParameter() method for testing.
         */
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        /*
        Verifying the output.
         */
        Assert.assertEquals("fail", expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#getParameter(StaplerRequest, String)} when the parameter value
     * for parameter "failureOnly" is null in the HTTP request header.
     */
    @Test
    public void getParametersWhenFailureOnlyIsNull () throws Exception {
        /*
        Mocking StaplerRequest object.
         */
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        String paramName = "failureOnly";
        String actualParamValue = null;
        /*
        Getting TestResultAction object as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null, null, null);
        /*
        Mocking the getParameter() method of StaplerRequest class to fetch the value of query parameter.
         */
        PowerMockito.doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        /*
        Calling the getParameter() method for testing.
         */
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction, "getParameter", staplerRequest, paramName);
        /*
        Verifying the output.
         */
        Assert.assertEquals("false", expectedParamValue);
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#buildFlapperDataset(StaplerRequest)} when no testcase flapped
     * and the a particular project was chosen.
     */
    @Test
    public void buildFlapperDatasetTestWithoutFlapper () throws Exception {
        /*
        Getting TestResultAction objects as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction1 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction2 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction3 = PowerMockito.spy(new TestResultAction(null, null, null));
        /*
        Generating Run objects.
         */
        Run<?, ?> run = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run1 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run2 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run3 = Whitebox.newInstance(FreeStyleBuild.class);
        /*
        Setting run number for the corresponding run objects.
         */
        run.number = 4;
        run1.number = 3;
        run2.number = 2;
        run3.number = 1;
        Whitebox.setInternalState(abstractTestResultAction, "run", run);
        Whitebox.setInternalState(abstractTestResultAction1, "run", run1);
        Whitebox.setInternalState(abstractTestResultAction2, "run", run2);
        Whitebox.setInternalState(abstractTestResultAction3, "run", run3);
        /*
        Mocking StaplerRequest object.
         */
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        /*
        Mocking getParameter() call.
         */
        PowerMockito.doReturn("com").when(abstractTestResultAction, "getParameter", staplerRequest, "projectLevel");
        PowerMockito.doReturn("fail").when(abstractTestResultAction, "getParameter", staplerRequest, "orderBy");
        /*
        Mocking reading of max number of previous builds to consider by reading system properties.
         */
        PowerMockito.spy(Integer.class);
        PowerMockito.when(Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE)).thenReturn(Integer.MAX_VALUE);
        /*
        Mocking getPreviousResult() to iterate over the previous builds.
         */
        PowerMockito.doReturn(abstractTestResultAction1).when(abstractTestResultAction).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction2).when(abstractTestResultAction1).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction3).when(abstractTestResultAction2).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(null).when(abstractTestResultAction3).getPreviousResult(AbstractTestResultAction.class);
        /*
        Mocking TestResult object.
         */
        TestResult testResult = PowerMockito.mock(TestResult.class);
        /*
        Mocking CaseResult object.
         */
        CaseResult caseResult1 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult2 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult3 = PowerMockito.mock(CaseResult.class);
        /*
        Making list of test cases.
         */
        List<CaseResult> caseResultList = new ArrayList<>();
        caseResultList.add(caseResult1);
        caseResultList.add(caseResult2);
        caseResultList.add(caseResult3);
        /*
        Mocking loading of xml file.
         */
        PowerMockito.doReturn(testResult).when(abstractTestResultAction).loadXml();
        PowerMockito.doReturn(testResult).when(abstractTestResultAction1).loadXml();
        PowerMockito.doReturn(testResult).when(abstractTestResultAction2).loadXml();
        PowerMockito.doReturn(testResult).when(abstractTestResultAction3).loadXml();
        /*
        Getting the list of failed test cases.
         */
        PowerMockito.doReturn(caseResultList).when(testResult).getFailedTests();
        /*
        Getting full classified testcase name for each testcase.
         */
        PowerMockito.doReturn("com.salesforce.hadoop.Class1.Test1").when(caseResult1).getFullName();
        PowerMockito.doReturn("org.apache.hbase.Class2.Test2").when(caseResult2).getFullName();
        PowerMockito.doReturn("com.salesforce.phoenix.Class3.Test3").when(caseResult3).getFullName();
        /*
        Getting the list of passed test cases.
         */
        PowerMockito.doReturn(new ArrayList<>()).when(testResult).getPassedTests();
        /*
        Calling buildFlapperDataset() method for testing.
         */
        XYSeriesCollection dataset = Whitebox.invokeMethod(abstractTestResultAction, "buildFlapperDataset", staplerRequest);
        /*
        Generating expected output object.
         */
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
        /*
        Verifying output.
         */
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

    /**
     * Testcase for testing the {AbstractTestResultAction#buildFlapperDataset(StaplerRequest)} when a testcase flapped
     * and all the projects were chosen.
     */
    @Test
    public void buildFlapperDatasetTestWithFlapper () throws Exception {
        /*
        Getting TestResultAction objects as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction1 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction2 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction3 = PowerMockito.spy(new TestResultAction(null, null, null));
        /*
        Generating Run objects.
         */
        Run<?, ?> run = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run1 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run2 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run3 = Whitebox.newInstance(FreeStyleBuild.class);
        /*
        Setting run number for the corresponding run objects.
         */
        run.number = 4;
        run1.number = 3;
        run2.number = 2;
        run3.number = 1;
        Whitebox.setInternalState(abstractTestResultAction, "run", run);
        Whitebox.setInternalState(abstractTestResultAction1, "run", run1);
        Whitebox.setInternalState(abstractTestResultAction2, "run", run2);
        Whitebox.setInternalState(abstractTestResultAction3, "run", run3);
        /*
        Mocking StaplerRequest object.
         */
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        /*
        Mocking getParameter() call.
         */
        PowerMockito.doReturn("AllProjects").when(abstractTestResultAction, "getParameter", staplerRequest, "projectLevel");
        PowerMockito.doReturn("flap").when(abstractTestResultAction, "getParameter", staplerRequest, "orderBy");
        /*
        Mocking reading of max number of previous builds to consider by reading system properties.
         */
        PowerMockito.spy(Integer.class);
        PowerMockito.when(Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE)).thenReturn(Integer.MAX_VALUE);
        /*
        Mocking getPreviousResult() to iterate over the previous builds.
         */
        PowerMockito.doReturn(abstractTestResultAction1).when(abstractTestResultAction).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction2).when(abstractTestResultAction1).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction3).when(abstractTestResultAction2).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(null).when(abstractTestResultAction3).getPreviousResult(AbstractTestResultAction.class);
        /*
        Mocking TestResult object.
         */
        TestResult testResult = PowerMockito.mock(TestResult.class);
        TestResult flapTestResult = PowerMockito.mock(TestResult.class);
        /*
        Mocking CaseResult object.
         */
        CaseResult caseResult1 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult2 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult3 = PowerMockito.mock(CaseResult.class);
        /*
        Generating the list of all test cases, failed test cases and passed test cases.
         */
        List<CaseResult> caseResultList = new ArrayList<>();
        caseResultList.add(caseResult1);
        caseResultList.add(caseResult2);
        caseResultList.add(caseResult3);
        List<CaseResult> failCaseResultList = new ArrayList<>();
        failCaseResultList.add(caseResult1);
        failCaseResultList.add(caseResult3);
        List<CaseResult> passCaseResultList = new ArrayList<>();
        passCaseResultList.add(caseResult2);
        /*
        Mocking loading of xml file.
         */
        PowerMockito.doReturn(testResult).when(abstractTestResultAction).loadXml();
        PowerMockito.doReturn(flapTestResult).when(abstractTestResultAction1).loadXml();
        PowerMockito.doReturn(testResult).when(abstractTestResultAction2).loadXml();
        PowerMockito.doReturn(testResult).when(abstractTestResultAction3).loadXml();
        /*
        Getting the list of failed test cases.
         */
        PowerMockito.doReturn(caseResultList).when(testResult).getFailedTests();
        PowerMockito.doReturn(failCaseResultList).when(flapTestResult).getFailedTests();
        /*
        Getting the name of test cases for all test cases.
         */
        PowerMockito.doReturn("com.salesforce.hadoop.Class1.Test1").when(caseResult1).getFullName();
        PowerMockito.doReturn("org.apache.hbase.Class2.Test2").when(caseResult2).getFullName();
        PowerMockito.doReturn("com.salesforce.phoenix.Class3.Test3").when(caseResult3).getFullName();
        /*
        Getting the list of passed test cases.
         */
        PowerMockito.doReturn(new ArrayList<>()).when(testResult).getPassedTests();
        PowerMockito.doReturn(passCaseResultList).when(flapTestResult).getPassedTests();
        /*
        Calling buildFlapperDataset() method for testing.
         */
        XYSeriesCollection dataset = Whitebox.invokeMethod(abstractTestResultAction, "buildFlapperDataset", staplerRequest);
        /*
        Generating the expected output.
         */
        XYSeriesCollection xySeriesCollection = new XYSeriesCollection();
        XYSeries xySeries;
        for (int series = 3; series >= 1; series--) {
            xySeries = new XYSeries(series);
            for (int index = 4; index >= 1; index--) {
                if (series == 3 && index == 3) {
                    xySeries.add(index, null);
                }
                else {
                    xySeries.add(index, series);
                }
            }
            xySeriesCollection.addSeries(xySeries);
        }
        xySeries = new XYSeries(0);
        for (int index = 4; index >= 1; index--) {
            xySeries.add(index, null);
        }
        xySeries.add(4.5, 3.5);
        xySeriesCollection.addSeries(xySeries);
        /*
        Verifying the output.
         */
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

    /**
     * Testcase for testing the {AbstractTestResultAction#buildDataSetPerProject(StaplerRequest)} when a particular
     * project was chosen.
     */
    @Test
    public void buildDatasetPerProjectTest () throws Exception {
        /*
        Getting TestResultAction objects as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction1 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction2 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction3 = PowerMockito.spy(new TestResultAction(null, null, null));
        /*
        Generating Run objects.
         */
        Run<?, ?> run = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run1 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run2 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run3 = Whitebox.newInstance(FreeStyleBuild.class);
        /*
        Setting run number for the corresponding run objects.
         */
        run.number = 4;
        run1.number = 3;
        run2.number = 2;
        run3.number = 1;
        Whitebox.setInternalState(abstractTestResultAction, "run", run);
        Whitebox.setInternalState(abstractTestResultAction1, "run", run1);
        Whitebox.setInternalState(abstractTestResultAction2, "run", run2);
        Whitebox.setInternalState(abstractTestResultAction3, "run", run3);
        /*
        Mocking StaplerRequest object.
         */
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        /*
        Mocking getParameter() call.
         */
        PowerMockito.doReturn("false").when(abstractTestResultAction, "getParameter", staplerRequest, "failureOnly");
        PowerMockito.doReturn("com").when(abstractTestResultAction, "getParameter", staplerRequest, "projectLevel");
        /*
        Mocking reading of max number of previous builds to consider by reading system properties.
         */
        PowerMockito.spy(Integer.class);
        PowerMockito.when(Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE)).thenReturn(Integer.MAX_VALUE);
        /*
        Mocking getPreviousResult() to iterate over the previous builds.
         */
        PowerMockito.doReturn(abstractTestResultAction1).when(abstractTestResultAction).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction2).when(abstractTestResultAction1).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction3).when(abstractTestResultAction2).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(null).when(abstractTestResultAction3).getPreviousResult(AbstractTestResultAction.class);
        /*
        Mocking TestResult object.
         */
        TestResult allFailTestResult = PowerMockito.mock(TestResult.class);
        TestResult notAllFailTestResult = PowerMockito.mock(TestResult.class);
        /*
        Mocking CaseResult object.
         */
        CaseResult caseResult1 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult2 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult3 = PowerMockito.mock(CaseResult.class);
        /*
        Generating the list of all test cases, failed test cases and passed test cases.
         */
        List<CaseResult> caseResultList = new ArrayList<>();
        caseResultList.add(caseResult1);
        caseResultList.add(caseResult2);
        caseResultList.add(caseResult3);
        List<CaseResult> failCaseResultList = new ArrayList<>();
        failCaseResultList.add(caseResult1);
        failCaseResultList.add(caseResult2);
        List<CaseResult> passCaseResultList = new ArrayList<>();
        passCaseResultList.add(caseResult3);
        /*
        Mocking loading of xml file.
         */
        PowerMockito.doReturn(allFailTestResult).when(abstractTestResultAction).loadXml();
        PowerMockito.doReturn(notAllFailTestResult).when(abstractTestResultAction1).loadXml();
        PowerMockito.doReturn(allFailTestResult).when(abstractTestResultAction2).loadXml();
        PowerMockito.doReturn(allFailTestResult).when(abstractTestResultAction3).loadXml();
        /*
        Getting the list of failed test cases.
         */
        PowerMockito.doReturn(caseResultList).when(allFailTestResult).getFailedTests();
        PowerMockito.doReturn(failCaseResultList).when(notAllFailTestResult).getFailedTests();
        /*
        Getting the full classified name of each test case.
         */
        PowerMockito.doReturn("com.salesforce.hadoop.Class1.Test1").when(caseResult1).getFullName();
        PowerMockito.doReturn("org.apache.hbase.Class2.Test2").when(caseResult2).getFullName();
        PowerMockito.doReturn("com.salesforce.phoenix.Class3.Test3").when(caseResult3).getFullName();
        /*
        Getting the list of all the passed test cases.
         */
        PowerMockito.doReturn(new ArrayList<>()).when(allFailTestResult).getPassedTests();
        PowerMockito.doReturn(passCaseResultList).when(notAllFailTestResult).getPassedTests();
        /*
        Getting the list of all skipped test cases.
         */
        PowerMockito.doReturn(new ArrayList<>()).when(allFailTestResult).getSkippedTests();
        PowerMockito.doReturn(new ArrayList<>()).when(notAllFailTestResult).getSkippedTests();
        /*
        Getting only the test case name for each test case.
         */
        PowerMockito.doReturn("Test1").when(caseResult1).getName();
        PowerMockito.doReturn("Test2").when(caseResult2).getName();
        PowerMockito.doReturn("Test3").when(caseResult3).getName();
        /*
        Calling buildDataSetPerProject() method for testing.
         */
        CategoryDataset actualDataset = Whitebox.invokeMethod(abstractTestResultAction, "buildDataSetPerProject", staplerRequest);
        /*
        Verifying expected output.
         */
        String[] rows = {"failed", "skipped", "total"};
        Run<?, ?>[] runs = {run3, run2, run1, run};
        Assert.assertEquals("Row count should be same", 3, actualDataset.getRowCount());
        Assert.assertEquals("Column count should be same", 4, actualDataset.getColumnCount());
        for (String rowKey : rows) {
            for (int index = 0; index < runs.length; index++) {
                if (rowKey.equals("failed")) {
                    ChartUtil.NumberOnlyBuildLabel label = new ChartUtil.NumberOnlyBuildLabel(runs[index]);
                    if (index == 2) {
                        Assert.assertEquals("Value for a cell should be same", 1, actualDataset.getValue(rowKey, label));
                    }
                    else {
                        Assert.assertEquals("Value for a cell should be same", 2, actualDataset.getValue(rowKey, label));
                    }
                }
                else if (rowKey.equals("total")) {
                    ChartUtil.NumberOnlyBuildLabel label = new ChartUtil.NumberOnlyBuildLabel(runs[index]);
                    if (index == 2) {
                        Assert.assertEquals("Value for a cell should be same", 1, actualDataset.getValue(rowKey, label));
                    }
                    else {
                        Assert.assertEquals("Value for a cell should be same", 0, actualDataset.getValue(rowKey, label));
                    }
                }
                else {
                    ChartUtil.NumberOnlyBuildLabel label = new ChartUtil.NumberOnlyBuildLabel(runs[index]);
                    Assert.assertEquals("Value for a cell should be same", 0, actualDataset.getValue(rowKey, label));
                }
            }
        }
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#buildLengthyTestDataset(StaplerRequest)} when all projects
     * were chosen and metric used was "mean".
     */
    @Test
    public void buildLengthyTestDatasetWithMeanAsMetric () throws Exception {
        /*
        Getting TestResultAction objects as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction1 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction2 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction3 = PowerMockito.spy(new TestResultAction(null, null, null));
        /*
        Generating Run objects.
         */
        Run<?, ?> run = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run1 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run2 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run3 = Whitebox.newInstance(FreeStyleBuild.class);
        /*
        Setting run number for the corresponding run objects.
         */
        run.number = 4;
        run1.number = 3;
        run2.number = 2;
        run3.number = 1;
        Whitebox.setInternalState(abstractTestResultAction, "run", run);
        Whitebox.setInternalState(abstractTestResultAction1, "run", run1);
        Whitebox.setInternalState(abstractTestResultAction2, "run", run2);
        Whitebox.setInternalState(abstractTestResultAction3, "run", run3);
        /*
        Mocking StaplerRequest object.
         */
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        /*
        Mocking getParameter() call.
         */
        PowerMockito.doReturn("AllProjects").when(abstractTestResultAction, "getParameter", staplerRequest, "projectLevel");
        PowerMockito.doReturn("mean").when(abstractTestResultAction, "getParameter", staplerRequest, "metricName");
        /*
        Mocking reading of max number of previous builds to consider by reading system properties.
         */
        PowerMockito.spy(Integer.class);
        PowerMockito.when(Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE)).thenReturn(Integer.MAX_VALUE);
        /*
        Mocking getPreviousResult() to iterate over the previous builds.
         */
        PowerMockito.doReturn(abstractTestResultAction1).when(abstractTestResultAction).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction2).when(abstractTestResultAction1).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction3).when(abstractTestResultAction2).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(null).when(abstractTestResultAction3).getPreviousResult(AbstractTestResultAction.class);
        /*
        Mocking TestResult object.
         */
        TestResult allPassTestResult = PowerMockito.mock(TestResult.class);
        TestResult notAllPassTestResult = PowerMockito.mock(TestResult.class);
        /*
        Mocking CaseResult object.
         */
        CaseResult caseResult1 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult2 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult3 = PowerMockito.mock(CaseResult.class);
        /*
        Generating the list of all test cases and passed test cases.
         */
        List<CaseResult> caseResultList = new ArrayList<>();
        caseResultList.add(caseResult1);
        caseResultList.add(caseResult2);
        caseResultList.add(caseResult3);
        List<CaseResult> passCaseResultList = new ArrayList<>();
        passCaseResultList.add(caseResult2);
        passCaseResultList.add(caseResult3);
        /*
        Mocking loading of xml file.
         */
        PowerMockito.doReturn(allPassTestResult).when(abstractTestResultAction).loadXml();
        PowerMockito.doReturn(notAllPassTestResult).when(abstractTestResultAction1).loadXml();
        PowerMockito.doReturn(allPassTestResult).when(abstractTestResultAction2).loadXml();
        PowerMockito.doReturn(allPassTestResult).when(abstractTestResultAction3).loadXml();
        /*
        Getting the list of passed test cases.
         */
        PowerMockito.doReturn(caseResultList).when(allPassTestResult).getPassedTests();
        PowerMockito.doReturn(passCaseResultList).when(notAllPassTestResult).getPassedTests();
        /*
        Getting the full classified name of all the test cases.
         */
        PowerMockito.doReturn("com.salesforce.hadoop.Class1.Test1").when(caseResult1).getFullName();
        PowerMockito.doReturn("org.apache.hbase.Class2.Test2").when(caseResult2).getFullName();
        PowerMockito.doReturn("com.salesforce.phoenix.Class3.Test3").when(caseResult3).getFullName();
        /*
        Getting only the name of all the test cases.
         */
        PowerMockito.doReturn("Test1").when(caseResult1).getName();
        PowerMockito.doReturn("Test2").when(caseResult2).getName();
        PowerMockito.doReturn("Test3").when(caseResult3).getName();
        /*
        Mocking the call to getDuration() method for each test case.
         */
        PowerMockito.doReturn(1.0f, 1.5f, 1.5f, 1.0f, 1.0f).when(caseResult1).getDuration();
        PowerMockito.doReturn(1.0f, 1.5f, 1.5f, 0.5f, 0.5f, 1.0f, 1.0f).when(caseResult2).getDuration();
        PowerMockito.doReturn(1.0f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f).when(caseResult3).getDuration();
        /*
        Calling buildLengthyTestDataset() method for testing.
         */
        CategoryDataset actualDataset = Whitebox.invokeMethod(abstractTestResultAction, "buildLengthyTestDataset", staplerRequest);
        /*
        Verifying whether the getDuration() method was called required number of times and when expected.
         */
        Mockito.verify(caseResult1, Mockito.times(5)).getDuration();
        Mockito.verify(caseResult2, Mockito.times(7)).getDuration();
        Mockito.verify(caseResult3, Mockito.times(7)).getDuration();
        /*
        Verifying the output.
         */
        String rowKey = "Lengthy Tests";
        Run<?, ?>[] runs = {run3, run2, run1, run};
        Assert.assertEquals("Row count should be same", 1, actualDataset.getRowCount());
        Assert.assertEquals("Column count should be same", 4, actualDataset.getColumnCount());
        for (int index = 0; index < runs.length; index++) {
            ChartUtil.NumberOnlyBuildLabel label = new ChartUtil.NumberOnlyBuildLabel(runs[index]);
            if (index == 0) {
                Assert.assertEquals("Value for a cell should be same", 0, actualDataset.getValue(rowKey, label));
            }
            else if (index == 1) {
                Assert.assertEquals("Value for a cell should be same", 3, actualDataset.getValue(rowKey, label));
            }
            else if (index == 2) {
                Assert.assertEquals("Value for a cell should be same", 1, actualDataset.getValue(rowKey, label));
            }
            else {
                Assert.assertEquals("Value for a cell should be same", 2, actualDataset.getValue(rowKey, label));
            }
        }
    }

    /**
     * Testcase for testing the {AbstractTestResultAction#buildLengthyTestDataset(StaplerRequest)} when a specific project
     * was chosen and metric used was "max".
     */
    @Test
    public void buildLengthyTestDatasetWithMaxAsMetric () throws Exception {
        /*
        Getting TestResultAction objects as it is the implementing class of AbstractTestResultAction. And suppressing the
        setResult() & onAttached() method which are called from inside the TestResultAction and AbstractTestResultAction
        Constructor.
         */
        MemberModifier.suppress(MemberMatcher.method(TestResultAction.class, "setResult", TestResult.class, TaskListener.class));
        MemberModifier.suppress(MemberMatcher.method(AbstractTestResultAction.class, "onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction1 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction2 = PowerMockito.spy(new TestResultAction(null, null, null));
        AbstractTestResultAction<?> abstractTestResultAction3 = PowerMockito.spy(new TestResultAction(null, null, null));
        /*
        Generating Run objects.
         */
        Run<?, ?> run = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run1 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run2 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?, ?> run3 = Whitebox.newInstance(FreeStyleBuild.class);
        /*
        Setting run number for the corresponding run objects.
         */
        run.number = 4;
        run1.number = 3;
        run2.number = 2;
        run3.number = 1;
        Whitebox.setInternalState(abstractTestResultAction, "run", run);
        Whitebox.setInternalState(abstractTestResultAction1, "run", run1);
        Whitebox.setInternalState(abstractTestResultAction2, "run", run2);
        Whitebox.setInternalState(abstractTestResultAction3, "run", run3);
        /*
        Mocking StaplerRequest object.
         */
        StaplerRequest staplerRequest = PowerMockito.mock(StaplerRequest.class);
        /*
        Mocking getParameter() call.
         */
        PowerMockito.doReturn("com").when(abstractTestResultAction, "getParameter", staplerRequest, "projectLevel");
        PowerMockito.doReturn("max").when(abstractTestResultAction, "getParameter", staplerRequest, "metricName");
        /*
        Mocking reading of max number of previous builds to consider by reading system properties.
         */
        PowerMockito.spy(Integer.class);
        PowerMockito.when(Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE)).thenReturn(Integer.MAX_VALUE);
        /*
        Mocking getPreviousResult() to iterate over the previous builds.
         */
        PowerMockito.doReturn(abstractTestResultAction1).when(abstractTestResultAction).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction2).when(abstractTestResultAction1).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(abstractTestResultAction3).when(abstractTestResultAction2).getPreviousResult(AbstractTestResultAction.class);
        PowerMockito.doReturn(null).when(abstractTestResultAction3).getPreviousResult(AbstractTestResultAction.class);
        /*
        Mocking TestResult object.
         */
        TestResult allPassTestResult = PowerMockito.mock(TestResult.class);
        TestResult notAllPassTestResult = PowerMockito.mock(TestResult.class);
        /*
        Mocking CaseResult object.
         */
        CaseResult caseResult1 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult2 = PowerMockito.mock(CaseResult.class);
        CaseResult caseResult3 = PowerMockito.mock(CaseResult.class);
        /*
        Generating the list of all test cases and passed test cases.
         */
        List<CaseResult> caseResultList = new ArrayList<>();
        caseResultList.add(caseResult1);
        caseResultList.add(caseResult2);
        caseResultList.add(caseResult3);
        List<CaseResult> passCaseResultList = new ArrayList<>();
        passCaseResultList.add(caseResult2);
        passCaseResultList.add(caseResult3);
        /*
        Mocking loading of xml file.
         */
        PowerMockito.doReturn(allPassTestResult).when(abstractTestResultAction).loadXml();
        PowerMockito.doReturn(notAllPassTestResult).when(abstractTestResultAction1).loadXml();
        PowerMockito.doReturn(allPassTestResult).when(abstractTestResultAction2).loadXml();
        PowerMockito.doReturn(allPassTestResult).when(abstractTestResultAction3).loadXml();
        /*
        Getting the list of passed test cases.
         */
        PowerMockito.doReturn(caseResultList).when(allPassTestResult).getPassedTests();
        PowerMockito.doReturn(passCaseResultList).when(notAllPassTestResult).getPassedTests();
        /*
        Getting full classified name of all the test cases.
         */
        PowerMockito.doReturn("com.salesforce.hadoop.Class1.Test1").when(caseResult1).getFullName();
        PowerMockito.doReturn("org.apache.hbase.Class2.Test2").when(caseResult2).getFullName();
        PowerMockito.doReturn("com.salesforce.phoenix.Class3.Test3").when(caseResult3).getFullName();
        /*
        Getting just the name of the testcase for all the test cases.
         */
        PowerMockito.doReturn("Test1").when(caseResult1).getName();
        PowerMockito.doReturn("Test2").when(caseResult2).getName();
        PowerMockito.doReturn("Test3").when(caseResult3).getName();
        /*
        Mocking the call to getDuration() method for each test case.
         */
        PowerMockito.doReturn(1.0f, 1.5f, 1.5f, 1.6f, 1.6f).when(caseResult1).getDuration();
        PowerMockito.doReturn(1.0f, 1.5f, 1.5f, 0.5f, 0.5f, 1.7f, 1.7f).when(caseResult2).getDuration();
        PowerMockito.doReturn(1.0f, 1.5f, 1.5f, 1.7f, 1.7f, 1.9f, 1.9f).when(caseResult3).getDuration();
        /*
        Calling buildLengthyTestDataset() method for testing.
         */
        CategoryDataset actualDataset = Whitebox.invokeMethod(abstractTestResultAction, "buildLengthyTestDataset", staplerRequest);
        /*
        Verifying whether the getDuration() method was called required number of times and when expected.
         */
        Mockito.verify(caseResult1, Mockito.times(5)).getDuration();
        Mockito.verify(caseResult2, Mockito.times(0)).getDuration();
        Mockito.verify(caseResult3, Mockito.times(7)).getDuration();
        /*
        verifying output.
         */
        String rowKey = "Lengthy Tests";
        Run<?, ?>[] runs = {run3, run2, run1, run};
        Assert.assertEquals("Row count should be same", 1, actualDataset.getRowCount());
        Assert.assertEquals("Column count should be same", 4, actualDataset.getColumnCount());
        for (int index = 0; index < runs.length; index++) {
            ChartUtil.NumberOnlyBuildLabel label = new ChartUtil.NumberOnlyBuildLabel(runs[index]);
            if (index == 0) {
                Assert.assertEquals("Value for a cell should be same", 0, actualDataset.getValue(rowKey, label));
            }
            else if (index == 1) {
                Assert.assertEquals("Value for a cell should be same", 2, actualDataset.getValue(rowKey, label));
            }
            else if (index == 2) {
                Assert.assertEquals("Value for a cell should be same", 1, actualDataset.getValue(rowKey, label));
            }
            else {
                Assert.assertEquals("Value for a cell should be same", 2, actualDataset.getValue(rowKey, label));
            }
        }
    }
}