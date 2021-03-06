/*
 * Copyright (c) 2013-2014, Neuro4j.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neuro4j.studio.debug.core.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.internal.debug.core.breakpoints.JavaLineBreakpoint;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
import org.eclipse.jdt.internal.debug.core.model.JDIThread;
import org.neuro4j.studio.core.ActionNode;
import org.neuro4j.studio.core.diagram.edit.parts.NodeBaseEditPart;
import org.neuro4j.studio.core.diagram.markers.MarkerMng;
import org.neuro4j.studio.core.util.ClassloaderHelper;
import org.neuro4j.studio.debug.core.BreakpoinMng;
import org.neuro4j.studio.debug.core.DebugExamplesPlugin;
import org.neuro4j.studio.debug.core.launching.IPDAConstants;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.ThreadStartEvent;

/**
 * PDA line breakpoint
 */
public abstract class FlowLineBreakpoint extends JavaLineBreakpoint
{
    public static final String BEEHIVE_BREAKPOINT_MARKER = DebugExamplesPlugin.getDefault().getID() +
            ".beehiveBreakpointMarker";

    protected static final String EXPIRED = DebugExamplesPlugin.getDefault().getID() +
            ".expired";

    protected static final String HIT_COUNT = DebugExamplesPlugin.getDefault()
            .getID() +
            ".hitCount";

    public static final String INSTALL_COUNT = DebugExamplesPlugin.getDefault()
            .getID() +
            ".installCount";
    private PDADebugElement beehiveElement;
    protected HashMap fRequestsByTarget;
    protected Set installedTargets = null;

    public FlowLineBreakpoint()
    {
        super();
    }

    @Override
    public boolean hasCondition() {
        // TODO Auto-generated method stub
        return super.hasCondition();

    }

    @Override
    public boolean isConditionEnabled() throws CoreException {
        if (!getMarker().exists()) {
            return false;
        }
        return super.isConditionEnabled();

    }

    @Override
    public boolean isConditionSuspendOnTrue() throws DebugException {
        // TODO Auto-generated method stub
        return super.isConditionSuspendOnTrue();

    }
    
    

//    private String getLocationByNode(ActionNode action)
//    {
//        return action.getId() + "=" + ClassloaderHelper.getCurrentResource().getProjectRelativePath().toPortableString();
//    }

    public FlowLineBreakpoint(String clazz) throws DebugException
    {
        this(MarkerManager.getBeehiveElementMarkerResource(clazz), new Integer(0), true, new HashMap<String, Object>());
    }

    public FlowLineBreakpoint(final IResource resource, final int hitCount, final boolean add, final Map<String, Object> attributes)
            throws DebugException
    {

        final BreakPointParamContainer container = BreakPointContainerFactory.getBreakPointContainer();

        IWorkspaceRunnable wr = new IWorkspaceRunnable()
        {
            public void run(IProgressMonitor monitor) throws CoreException
            {

                setMarker(resource.createMarker("org.eclipse.jdt.debug.javaLineBreakpointMarker"));
                // setMarker(resource.createMarker("org.neuro4j.studio.debug.core.flowLineBreakpointMarker"));

                addLineBreakpointAttributes(attributes, getModelIdentifier(),
                        true, container.getLineNumber(), container.getStartChar(), container.getEndChar());
                addTypeNameAndHitCount(attributes, container.getBaseClassName(), hitCount);
               // attributes.put("locations", getLocationByNode(action));
                attributes.put("flowType", BreakpoinMng.DEBUGSERV_STRING);
                // attributes.put("uuids", action.getId());
                attributes.put("org.eclipse.jdt.debug.core.suspendPolicy", new Integer(
                        getDefaultSuspendPolicy()));
                setAttribute(IBreakpoint.ID, getModelIdentifier());
                ensureMarker().setAttributes(attributes);
                DebugPlugin.getDefault().getBreakpointManager().addBreakpoint(FlowLineBreakpoint.this);
                register(add);
            }
        };
        run(getMarkerRule(resource), wr);
    }

    public FlowLineBreakpoint(final JavaLineBreakpoint javaBp)
            throws DebugException
    {

        IWorkspaceRunnable wr = new IWorkspaceRunnable()
        {
            public void run(IProgressMonitor monitor) throws CoreException
            {
                setMarker(javaBp.getMarker());
                setCondition(javaBp.getCondition());
                setConditionEnabled(javaBp.isConditionEnabled());
                setSuspendPolicy(javaBp.getSuspendPolicy());
                setHitCount(javaBp.getHitCount());
                setConditionSuspendOnTrue(javaBp.isConditionSuspendOnTrue());

                setAttribute(IBreakpoint.ID, getModelIdentifier());
                ensureMarker().setAttributes(javaBp.getMarker().getAttributes());
            }
        };
        run(getMarkerRule(javaBp.getMarker().getResource()), wr);
    }

    public boolean handleBreakpointEvent(Event event, JDIThread thread, boolean suspendVote)
    {
        expireHitCount(event);
        return !suspend(thread, suspendVote);
    }

    private boolean processCheckIfSuspend(JDIThread thread)
    {
        try {
            if (thread.getStackFrames() == null || thread.getStackFrames().length == 0)
            {
                return true;
            }
            JDIStackFrame sframe = (JDIStackFrame) thread.getStackFrames()[0];

            String uuid = BreakpoinMng.getInstance().getCurrentUuid(sframe);

            if (BreakpoinMng.getInstance().isUUIDInBreakpoint(uuid))
            {
                return true;
            }

        } catch (DebugException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (CoreException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return false;
    }

    protected boolean suspend(JDIThread thread, boolean suspendVote)
    {
        if (!getMarker().exists()) {
            return false;
        }

        boolean suspend = thread.handleSuspendForBreakpoint(this, suspendVote);

        boolean needSusspend = processCheckIfSuspend(thread);
        if (needSusspend)
        {
            try {
                String uuid = BreakpoinMng.getInstance().getCurrentUuid((JDIStackFrame) thread.getTopStackFrame());
                NodeBaseEditPart editPart = (NodeBaseEditPart) MarkerMng.getInstance().getEditPartAndMark(uuid);
                if (editPart != null)
                {
                    BreakpoinMng.getInstance().suspend(editPart);
                }
                // else {
                // BreakpoinMng.getInstance().resume();
                // }
                System.out.println(uuid);
            } catch (DebugException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (CoreException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return true;
        } else {
            // thread.resume();
            return false;
        }


    }

    public String getCondition()
            throws CoreException
    {
        return super.getCondition();

    }

    public String getModelIdentifier()
    {
        return IPDAConstants.ID_PDA_DEBUG_MODEL;
    }

    public boolean isInstalled()
            throws CoreException
    {
        return ensureMarker().getAttribute(INSTALL_COUNT, 0) > 0;
    }

    public void addToTarget(FlowDebugTarget target)
            throws CoreException
    {
        if (!isInstalledIn(target))
        {
            setInstalledIn(target, true);

            incrementInstallCount();
        }
    }

    @Override
    protected IJavaProject getJavaProject(IJavaStackFrame stackFrame) {
    	return computeJavaProject(stackFrame);
        //return super.getJavaProject(stackFrame);
    }

    private IJavaProject computeJavaProject(IJavaStackFrame stackFrame) {
        ILaunch launch = stackFrame.getLaunch();
        if (launch == null) {
            return null;
        }
        try {
           String projectName =  launch.getLaunchConfiguration().getAttribute("org.eclipse.jdt.launching.PROJECT_ATTR", "");
           if (projectName != null)
           {
               IJavaProject jProject =  ClassloaderHelper.getJavaProject(projectName);
               if (jProject != null)
               {
                   return jProject;
               }
           }
           
        } catch (CoreException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        ISourceLocator locator= launch.getSourceLocator();
        if (locator == null)
            return null;
        
        Object sourceElement= null;
        try {
            if (locator instanceof ISourceLookupDirector && !stackFrame.isStatic()) {
                IJavaType thisType = stackFrame.getThis().getJavaType();
                if (thisType instanceof IJavaReferenceType) {
                    String[] sourcePaths= ((IJavaReferenceType) thisType).getSourcePaths(null);
                    if (sourcePaths != null && sourcePaths.length > 0) {
                        sourceElement= ((ISourceLookupDirector) locator).getSourceElement(sourcePaths[0]);
                    }
                }
            }
        } catch (DebugException e) {
            DebugPlugin.log(e);
        }
        if (sourceElement == null) {
            sourceElement = locator.getSourceElement(stackFrame);
        }
        if (!(sourceElement instanceof IJavaElement) && sourceElement instanceof IAdaptable) {
            Object element= ((IAdaptable)sourceElement).getAdapter(IJavaElement.class);
            if (element != null) {
                sourceElement= element;
            }
        }
        if (sourceElement instanceof IJavaElement) {
            return ((IJavaElement) sourceElement).getJavaProject();
        } else if (sourceElement instanceof IResource) {
            IJavaProject project = JavaCore.create(((IResource)sourceElement).getProject());
            if (project.exists()) {
                return project;
            }
        }
        
        
        return null;
    }
    
    protected void decrementInstallCount()
            throws CoreException
    {
        int count = getInstallCount();
        if (count > 0)
        {
            setAttribute(INSTALL_COUNT, count - 1);
        }
    }

    protected void incrementInstallCount()
            throws CoreException
    {
        int count = getInstallCount();
        setAttribute(INSTALL_COUNT, count + 1);
    }

    public int getInstallCount()
            throws CoreException
    {
        int markerInstallCount = ensureMarker().getAttribute(INSTALL_COUNT, 0);
        int registeredTargetsCount = this.installedTargets != null ? this.installedTargets
                .size() :
                0;

        if (markerInstallCount != registeredTargetsCount)
        {
            ensureMarker().setAttribute(INSTALL_COUNT, registeredTargetsCount);
            return registeredTargetsCount;
        }
        return markerInstallCount;
    }

    public boolean handleEvent(Event event, JDIDebugTarget target, boolean suspendVote, EventSet eventSet)
    {
        if ((event instanceof ClassPrepareEvent)) {
            return handleClassPrepareEvent((ClassPrepareEvent) event, target,
                    suspendVote);
        }
        ThreadReference threadRef = ((LocatableEvent) event).thread();
        JDIThread thread = target.findThread(threadRef);

        if (thread == null)
        {
            try
            {
                Job.getJobManager().join(ThreadStartEvent.class, null);
            } catch (OperationCanceledException localOperationCanceledException) {
            } catch (InterruptedException localInterruptedException) {
            }
            thread = target.findThread(threadRef);
        }
        if ((thread == null) || (thread.isIgnoringBreakpoints())) {
            return true;
        }

        return handleBreakpointEvent(event, thread, suspendVote);
    }


    protected boolean isInstalledIn(FlowDebugTarget target)
    {
        return (this.installedTargets != null) && (this.installedTargets.contains(target));
    }

    @Override
    public void removeFromTarget(JDIDebugTarget target) throws CoreException {
        // TODO Auto-generated method stub
        super.removeFromTarget(target);
        // MarkerMng.getInstance().removeMarker(this.getMarker());
    }

    protected void setInstalledIn(FlowDebugTarget target, boolean installed)
    {
        if (installed)
        {
            if (this.installedTargets == null)
            {
                this.installedTargets = new HashSet();
            }
            this.installedTargets.add(target);
        }
        else if (this.installedTargets != null)
        {
            this.installedTargets.remove(target);
        }
    }

    @Override
    public int getLineNumber() throws CoreException {
        // TODO Auto-generated method stub
        return super.getLineNumber();
    }

    @Override
    public int getCharStart() throws CoreException {
        // TODO Auto-generated method stub
        return super.getCharStart();
    }

    @Override
    public int getCharEnd() throws CoreException {
        // TODO Auto-generated method stub
        return super.getCharEnd();
    }

    @Override
    protected boolean suspendForEvent(Event event, JDIThread thread,
            boolean suspendVote) {
        // TODO Auto-generated method stub
        return super.suspendForEvent(event, thread, suspendVote);
    }

    @Override
    public void eventSetComplete(Event event, JDIDebugTarget target,
            boolean suspend, EventSet eventSet) {
        // TODO Auto-generated method stub
        super.eventSetComplete(event, target, suspend, eventSet);
    }

    @Override
    public boolean handleClassPrepareEvent(ClassPrepareEvent event,
            JDIDebugTarget target, boolean suspendVote) {
        System.out.println("handleClassPrepareEvent");
        return super.handleClassPrepareEvent(event, target, suspendVote);
    }

    @Override
    public void addInstanceFilter(IJavaObject object) throws CoreException {
        System.out.println("addInstanceFilter");
        super.addInstanceFilter(object);
    }

    @Override
    public synchronized void addBreakpointListener(String identifier)
            throws CoreException {
        System.out.println("addBreakpointListener");
        super.addBreakpointListener(identifier);
    }

    @Override
    public IMarker getMarker() {
        return super.getMarker();
    }

    public void connectBreakpoint()
    {
        IMarker marker = getMarker();
        String uuids;

        uuids = (String) marker.getAttribute("uuids", "");
        String[] u = uuids.split(" ");
        for (String uuid : u)
        {
            if (uuid.length() > 20)
            {
                BreakpoinMng.getInstance().registerUUIDForSuspend(uuid);

                NodeBaseEditPart part = (NodeBaseEditPart) MarkerMng.getInstance().getEditPartAndMark(uuid);
                if (part != null)
                {
                    part.setConnected();
                }
            }
        }

    }

}
