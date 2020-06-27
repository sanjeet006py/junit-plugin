package hudson.tasks.test;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import  org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.powermock.api.mockito.PowerMockito.*;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.ArrayList;
import java.util.Collection;
import static org.junit.Assert.*;


@RunWith(PowerMockRunner.class)
@PrepareForTest({TestResult.class, SuiteResult.class, TestResultAction.class, AbstractTestResultAction.class})
public class AbstractTestResultActionTest {

    TestResult r;
    TaskListener taskListener;
    Collection<SuiteResult> suiteList;
    SuiteResult suiteResult1;
    SuiteResult suiteResult2;
    SuiteResult suiteResult3;

    @Before
    public void setUp() throws Exception {
        r = mock(TestResult.class);
        suiteResult1 = mock(SuiteResult.class);
        suiteResult2 = mock(SuiteResult.class);
        suiteResult3 = mock(SuiteResult.class);
        taskListener = mock(TaskListener.class);
    }

    @Test
    public void getProjectListTest() {

        suppress(method(TestResultAction.class, "setResult", TestResult.class ,TaskListener.class));
        suppress(method(AbstractTestResultAction.class,"onAttached"));
        AbstractTestResultAction<?> abstractTestResultAction = spy(new TestResultAction(null,null,null));
        suiteList = new ArrayList<>();
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

    }
}