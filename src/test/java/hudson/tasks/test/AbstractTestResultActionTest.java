package hudson.tasks.test;

import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.util.DataSetBuilder;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.powermock.api.mockito.PowerMockito.*;

import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;


@RunWith(PowerMockRunner.class)
@PrepareForTest({TestResult.class, SuiteResult.class, TestResultAction.class, AbstractTestResultAction.class, Integer.class, Logger.class, FreeStyleBuild.class})
public class AbstractTestResultActionTest {

    private static Logger LOGGER;

    @BeforeClass
    public static void setUp(){
        LOGGER = mock(Logger.class);
        doNothing().when(LOGGER).log(Level.FINER,"",new Object[]{1,1});
        mockStatic(Logger.class);
        when(Logger.getLogger(AbstractTestResultAction.class.getName())).thenReturn(LOGGER);
    }

    @Test
    public void getProjectListTest() {

        TestResult r = mock(TestResult.class);
        SuiteResult suiteResult1 = mock(SuiteResult.class);
        SuiteResult suiteResult2 = mock(SuiteResult.class);
        SuiteResult suiteResult3 = mock(SuiteResult.class);
        suppress(method(TestResultAction.class, "setResult", TestResult.class ,TaskListener.class));
        suppress(method(AbstractTestResultAction.class,"onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = spy(new TestResultAction(null,null,null));
        Collection<SuiteResult> suiteList = new ArrayList<>();
        suiteList.add(suiteResult1);
        suiteList.add(suiteResult2);
        suiteList.add(suiteResult3);
        doReturn(r).when(abstractTestResultAction).loadXml();
        doReturn(suiteList).when(r).getSuites();
        doReturn("com.salesforce.hadoop.LoadTest").when(suiteResult1).getName();
        doReturn("org.apache.hbase.QueryTest").when(suiteResult2).getName();
        doReturn("com.salesforce.phoenix.UITest").when(suiteResult3).getName();
        String[] expectedProjectList = abstractTestResultAction.getProjectList();
        String[] actualProjectList = {"com", "com.salesforce", "com.salesforce.hadoop", "com.salesforce.phoenix", "org",
                "org.apache", "org.apache.hbase"};
        assertArrayEquals("Both arrays should be same",actualProjectList,expectedProjectList);
        Mockito.verify(r).getSuites();
        Mockito.verify(suiteResult1).getName();
        Mockito.verify(suiteResult2).getName();
        Mockito.verify(suiteResult3).getName();
    }

    @Test
    public void getParametersWhenParamValueIsNotNull() throws Exception {

        StaplerRequest staplerRequest = mock(StaplerRequest.class);
        String paramName = "projectLevel";
        String actualParamValue = "AllProjects";
        suppress(method(TestResultAction.class, "setResult", TestResult.class ,TaskListener.class));
        suppress(method(AbstractTestResultAction.class,"onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null,null,null);
        doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction,"getParameter",staplerRequest,paramName);
        assertEquals(actualParamValue,expectedParamValue);
    }

    @Test
    public void getParametersWhenParamValueIsNull() throws Exception {

        StaplerRequest staplerRequest = mock(StaplerRequest.class);
        String paramName = "metricName";
        String actualParamValue = null;
        suppress(method(TestResultAction.class, "setResult", TestResult.class ,TaskListener.class));
        suppress(method(AbstractTestResultAction.class,"onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = new TestResultAction(null,null,null);
        doReturn(actualParamValue).when(staplerRequest).getParameter(paramName);
        String expectedParamValue = Whitebox.invokeMethod(abstractTestResultAction,"getParameter",staplerRequest,paramName);
        assertEquals("mean",expectedParamValue);
    }

    @Test
    public void buildFlapperDatasetTest() throws Exception {
        suppress(method(TestResultAction.class, "setResult", TestResult.class ,TaskListener.class));
        suppress(method(AbstractTestResultAction.class,"onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = spy(new TestResultAction(null,null,null));
        AbstractTestResultAction<?> abstractTestResultAction1 = spy(new TestResultAction(null,null,null));
        AbstractTestResultAction<?> abstractTestResultAction2 = spy(new TestResultAction(null,null,null));
        AbstractTestResultAction<?> abstractTestResultAction3 = spy(new TestResultAction(null,null,null));
        Run<?,?> run = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?,?> run1 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?,?> run2 = Whitebox.newInstance(FreeStyleBuild.class);
        Run<?,?> run3 = Whitebox.newInstance(FreeStyleBuild.class);
        run.number = 4;
        run1.number = 3;
        run2.number = 2;
        run3.number = 1;
        Whitebox.setInternalState(abstractTestResultAction,"run",run);
        Whitebox.setInternalState(abstractTestResultAction1,"run",run1);
        Whitebox.setInternalState(abstractTestResultAction2,"run",run2);
        Whitebox.setInternalState(abstractTestResultAction3,"run",run3);
        StaplerRequest staplerRequest = mock(StaplerRequest.class);
        doReturn("AllProjects","fail").when(abstractTestResultAction,"getParameter",staplerRequest,"projectLevel");
        spy(Integer.class);
        when(Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE)).thenReturn(Integer.MAX_VALUE);
        doReturn(abstractTestResultAction1).when(abstractTestResultAction).getPreviousResult(AbstractTestResultAction.class);
        doReturn(abstractTestResultAction2).when(abstractTestResultAction1).getPreviousResult(AbstractTestResultAction.class);
        doReturn(abstractTestResultAction3).when(abstractTestResultAction2).getPreviousResult(AbstractTestResultAction.class);
        doReturn(null).when(abstractTestResultAction3).getPreviousResult(AbstractTestResultAction.class);
        TestResult r = mock(TestResult.class);
        CaseResult caseResult1 = mock(CaseResult.class);
        CaseResult caseResult2 = mock(CaseResult.class);
        CaseResult caseResult3 = mock(CaseResult.class);
        List<CaseResult> caseResultList = new ArrayList<>();
        caseResultList.add(caseResult1);
        caseResultList.add(caseResult2);
        caseResultList.add(caseResult3);
        doReturn(r).when(abstractTestResultAction).loadXml();
        doReturn(r).when(abstractTestResultAction1).loadXml();
        doReturn(r).when(abstractTestResultAction2).loadXml();
        doReturn(r).when(abstractTestResultAction3).loadXml();
        doReturn(caseResultList).when(r).getFailedTests();
        doReturn("com.salesforce.hadoop.Class1.Test1").when(caseResult1).getFullName();
        doReturn("org.apache.hbase.Class2.Test2").when(caseResult2).getFullName();
        doReturn("com.salesforce.phoenix.Class3.Test3").when(caseResult3).getFullName();
        doReturn(new ArrayList<>()).when(r).getPassedTests();
        XYSeriesCollection dataset = Whitebox.invokeMethod(abstractTestResultAction,"buildFlapperDataset",staplerRequest);
        XYSeriesCollection xySeriesCollection = new XYSeriesCollection();
        XYSeries xySeries;
        for(int series = 3;series>=1;series--){
            xySeries = new XYSeries(series);
            for(int index = 4; index>=1; index--){
                xySeries.add(index,series);
            }
            xySeriesCollection.addSeries(xySeries);
        }
        xySeries = new XYSeries(0);
        for(int index = 4; index>=1;index--){
            xySeries.add(index,null);
        }
        xySeries.add(4.5,3.5);
        xySeriesCollection.addSeries(xySeries);
        assertEquals("collection size should be same",xySeriesCollection.getSeriesCount(),dataset.getSeriesCount());
        for(int series = 0;series<xySeriesCollection.getSeriesCount();series++){
            XYSeries expectedXYSeries = xySeriesCollection.getSeries(series);
            XYSeries actualXYSeries = dataset.getSeries(series);
            assertEquals("Series size should be same",expectedXYSeries.getItemCount(),actualXYSeries.getItemCount());
            for(int item = 0;item<expectedXYSeries.getItemCount();item++){
                assertEquals("X value should be same", expectedXYSeries.getX(item),actualXYSeries.getX(item));
                assertEquals("Y value should be same", expectedXYSeries.getY(item),actualXYSeries.getY(item));
            }
        }
    }
}