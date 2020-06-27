/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.junit.JUnitResultArchiver;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Project action object from test reporter, such as {@link JUnitResultArchiver},
 * which displays the trend report on the project top page.
 *
 * <p>
 * This works with any {@link AbstractTestResultAction} implementation.
 *
 * @author Kohsuke Kawaguchi
 */
public class TestResultProjectAction implements Action {
    /**
     * Project that owns this action.
     *
     * @since 1.2-beta-1
     */
    public final Job<?, ?> job;

    @Deprecated
    public final AbstractProject<?, ?> project;

    /**
     * @since 1.2-beta-1
     */
    public TestResultProjectAction(Job<?, ?> job) {
        this.job = job;
        project = job instanceof AbstractProject ? (AbstractProject) job : null;
    }

    @Deprecated
    public TestResultProjectAction(AbstractProject<?, ?> project) {
        this((Job) project);
    }

    /**
     * No task list item.
     */
    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Test Report";
    }

    public String getUrlName() {
        return "test";
    }

    public AbstractTestResultAction getLastTestResultAction() {
        final Run<?, ?> tb = job.getLastSuccessfulBuild();

        Run<?, ?> b = job.getLastBuild();
        while (b != null) {
            AbstractTestResultAction a = b.getAction(AbstractTestResultAction.class);
            if (a != null && (!b.isBuilding()))
                return a;
            if (b == tb)
                // if even the last successful build didn't produce the test result,
                // that means we just don't have any tests configured.
                return null;
            b = b.getPreviousBuild();
        }

        return null;
    }

    /**
     * Display the test result trend.
     */
    public void doTrend(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        AbstractTestResultAction a = getLastTestResultAction();
        if (a != null)
            a.doGraph(req, rsp);
        else
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Generates the clickable map HTML fragment for {@link #doTrend(StaplerRequest, StaplerResponse)}.
     */
    public void doTrendMap(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        AbstractTestResultAction a = getLastTestResultAction();
        if (a != null)
            a.doGraphMap(req, rsp);
        else
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Changes the test result report display mode.
     */
    public void doFlipTrend(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        boolean failureOnly = false;

        // check the current preference value
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(FAILURE_ONLY_COOKIE))
                    failureOnly = Boolean.parseBoolean(cookie.getValue());
            }
        }

        // flip!
        failureOnly = !failureOnly;

        // set the updated value
        addCookie(req, rsp, FAILURE_ONLY_COOKIE, String.valueOf(failureOnly));

        // back to the project page
        rsp.sendRedirect("..");
    }

    public void doFailFlapFlip(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String orderBy = FAILMETRIC;

        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(ORDER_BY_COOKIE))
                    orderBy = cookie.getValue();
            }
        }

        if (orderBy.equals(FAILMETRIC)) {
            orderBy = FLAPMETRIC;
        }
        else {
            orderBy = FAILMETRIC;
        }
        addCookie(req, rsp, ORDER_BY_COOKIE, orderBy);
        rsp.sendRedirect("..");
    }

    /**
     * Method to modify cookies for displaying requested trend.
     *
     * @param req The HTTP request message incorporating api call this method.
     * @param rsp The HTTP response message.
     * @throws IOException Can occur while redirecting.
     */
    public void doSelectInput(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        AbstractTestResultAction<?> a = getLastTestResultAction();
        String[] projectList;
        if (a != null) {
            projectList = a.getProjectList();
            String projectLevel = getParameter(req, PROJECTLEVEL);
            if (Arrays.binarySearch(projectList, projectLevel) < 0) {
                projectLevel = ALL_PROJECTS;
            }
            String trendType = getParameter(req, TRENDTYPE);
            if (!trendType.equals(BUILD_ANALYSIS) && !trendType.equals(LENGTHY_TESTS_MEAN) && !trendType.equals(FLAKY_TESTS)) {
                trendType = BUILD_ANALYSIS;
            }
            int indx = trendType.lastIndexOf('_');
            String metricName;
            if (indx >= 0) {
                metricName = trendType.substring(indx + 1);
                trendType = trendType.substring(0, indx);
                addCookie(req, rsp, METRIC_NAME_COOKIE, metricName);
            }
            if (projectLevel != null) {
                addCookie(req, rsp, PROJECT_LEVEL_COOKIE, projectLevel);
            }
            if (trendType != null) {
                addCookie(req, rsp, TREND_TYPE_COOKIE, trendType);
            }
            rsp.sendRedirect("..");
        }
        else {
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * Method to add overwritten cookie to the response.
     *
     * @param req         The HTTP request message containing api call for
     *                    {@link #doSelectInput(StaplerRequest, StaplerResponse)}.
     * @param rsp         The HTTP response message.
     * @param cookieName  Name of the cookie to be modified.
     * @param cookieValue New value of the cookie after modification.
     */
    private void addCookie(StaplerRequest req, StaplerResponse rsp, String cookieName, String cookieValue) {
        Cookie cookie = new Cookie(cookieName, cookieValue);
        List anc = req.getAncestors();
        Ancestor a = (Ancestor) anc.get(anc.size() - 2);
        cookie.setPath(a.getUrl()); // just for this project
        cookie.setMaxAge(60 * 60 * 24 * 365); // 1 year
        rsp.addCookie(cookie);
    }

    /**
     * Method to extract query parameter from the url and if missing then return default value.
     *
     * @param req       The HTTP request message containing query parameter.
     * @param paramName The query parameter whose value needs to be extracted.
     * @return The value of the query parameter specified as paramName.
     */
    private String getParameter(StaplerRequest req, String paramName) {
        String paramValue = req.getParameter(paramName);

        /*
         * If the requested query parameter is absent as user deliberately fired url with less no. of parameters
         * the these default values are assigned.
         */
        if (paramValue == null) {
            if (paramName.equals(PROJECTLEVEL))
                return ALL_PROJECTS;
            else if (paramName.equals(TRENDTYPE))
                return BUILD_ANALYSIS;
        }
        return paramValue;
    }

    private static final String FAILURE_ONLY_COOKIE = "TestResultAction_failureOnly";
    private static final String PROJECT_LEVEL_COOKIE = "TestResultAction_projectLevel";
    private static final String TREND_TYPE_COOKIE = "TestResultAction_trendType";
    private static final String METRIC_NAME_COOKIE = "TestResultAction_metricName";
    private static final String ORDER_BY_COOKIE = "TestResultAction_orderBy";
    private static final String ALL_PROJECTS = "AllProjects";
    private static final String BUILD_ANALYSIS = "BuildAnalysis";
    private static final String LENGTHY_TESTS_MEAN = "LengthyTests_mean";
    private static final String FLAKY_TESTS = "FlakyTests";
    private static final String FAILMETRIC = "fail";
    private static final String FLAPMETRIC = "flap";
    private static final String PROJECTLEVEL = "projectLevel";
    private static final String TRENDTYPE = "trendType";
}
