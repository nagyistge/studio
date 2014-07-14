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
package org.neuro4j.studio.debug.ui.views;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.internal.ui.actions.ActionMessages;
import org.eclipse.debug.internal.ui.viewers.FindElementDialog;
import org.eclipse.debug.internal.ui.viewers.model.ILabelUpdateListener;
import org.eclipse.debug.internal.ui.viewers.model.TimeTriggeredProgressMonitorDialog;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IChildrenUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.ILabelUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelDelta;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelDeltaVisitor;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerUpdateListener;
import org.eclipse.debug.internal.ui.viewers.model.provisional.ModelDelta;
import org.eclipse.debug.internal.ui.viewers.model.provisional.VirtualItem;
import org.eclipse.debug.internal.ui.viewers.model.provisional.VirtualTreeModelViewer;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IUpdate;

import com.ibm.icu.text.MessageFormat;

/**
 * Action which prompts user with a filtered list selection dialog to find an element in tree.
 * 
 * @since 3.3
 */
public class VirtualFindAction extends Action implements IUpdate {

    private TreeModelViewer fClientViewer;

    private class VirtualViewerListener implements IViewerUpdateListener, ILabelUpdateListener {

        private boolean fViewerUpdatesComplete = false;
        private boolean fLabelUpdatesComplete = false;
        private IProgressMonitor fProgressMonitor;
        private int fRemainingUpdatesCount = 0;

        public void labelUpdateStarted(ILabelUpdate update) {
        }

        public void labelUpdateComplete(ILabelUpdate update) {
            incrementProgress(1);
        }

        public void labelUpdatesBegin() {
            fLabelUpdatesComplete = false;
        }

        public void labelUpdatesComplete() {
            fLabelUpdatesComplete = true;
            completeProgress();
        }

        public void updateStarted(IViewerUpdate update) {
        }

        public void updateComplete(IViewerUpdate update) {
            if (update instanceof IChildrenUpdate) {
                incrementProgress(((IChildrenUpdate) update).getLength());
            }
        }

        public void viewerUpdatesBegin() {
            fViewerUpdatesComplete = false;
        }

        public void viewerUpdatesComplete() {
            fViewerUpdatesComplete = true;
            completeProgress();
        }

        private void completeProgress() {
            IProgressMonitor pm;
            synchronized (this) {
                pm = fProgressMonitor;
            }
            if (pm != null && fLabelUpdatesComplete && fViewerUpdatesComplete) {
                pm.done();
            }
        }

        private void incrementProgress(int count) {
            IProgressMonitor pm;
            synchronized (this) {
                pm = fProgressMonitor;
                fRemainingUpdatesCount -= count;
            }
            if (pm != null && fLabelUpdatesComplete && fViewerUpdatesComplete) {
                pm.worked(count);
            }
        }

    }

    private static class FindLabelProvider extends LabelProvider {
        private VirtualTreeModelViewer fVirtualViewer;
        private Map fTextCache = new HashMap();

        public FindLabelProvider(VirtualTreeModelViewer viewer, List items) {
            fVirtualViewer = viewer;
            for (int i = 0; i < items.size(); i++) {
                VirtualItem item = (VirtualItem) items.get(i);
                fTextCache.put(item, fVirtualViewer.getText(item, 0));
            }
        }

        public Image getImage(Object element) {
            return fVirtualViewer.getImage((VirtualItem) element, 0);
        }

        public String getText(Object element) {
            return (String) fTextCache.get(element);
        }
    }

    public VirtualFindAction(TreeModelViewer viewer) {
        fClientViewer = viewer;

        setText(ActionMessages.FindAction_0);
        setId(DebugUIPlugin.getUniqueIdentifier() + ".FindElementAction"); //$NON-NLS-1$
        PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IDebugHelpContextIds.FIND_ELEMENT_ACTION);
        setActionDefinitionId(IWorkbenchCommandConstants.EDIT_FIND_AND_REPLACE);
        fClientViewer = viewer;
    }

    private VirtualTreeModelViewer initVirtualViewer(TreeModelViewer clientViewer, VirtualViewerListener listener) {
        Object input = clientViewer.getInput();
        ModelDelta stateDelta = new ModelDelta(input, IModelDelta.NO_CHANGE);
        clientViewer.saveElementState(TreePath.EMPTY, stateDelta, IModelDelta.EXPAND);
        listener.fRemainingUpdatesCount = calcUpdatesCount(stateDelta);
        VirtualTreeModelViewer fVirtualViewer = new VirtualTreeModelViewer(
                clientViewer.getDisplay(),
                SWT.NONE,
                clientViewer.getPresentationContext());
        fVirtualViewer.setFilters(clientViewer.getFilters());
        fVirtualViewer.addViewerUpdateListener(listener);
        fVirtualViewer.addLabelUpdateListener(listener);
        String[] columns = clientViewer.getPresentationContext().getColumns();
        fVirtualViewer.setInput(input);
        if (fVirtualViewer.canToggleColumns()) {
            fVirtualViewer.setShowColumns(clientViewer.isShowColumns());
            fVirtualViewer.setVisibleColumns(columns);
        }
        fVirtualViewer.updateViewer(stateDelta);
        return fVirtualViewer;
    }

    public void run() {
        final VirtualViewerListener listener = new VirtualViewerListener();
        VirtualTreeModelViewer virtualViewer = initVirtualViewer(fClientViewer, listener);

        ProgressMonitorDialog dialog = new TimeTriggeredProgressMonitorDialog(fClientViewer.getControl().getShell(), 500);
        final IProgressMonitor monitor = dialog.getProgressMonitor();
        dialog.setCancelable(true);

        try {
            dialog.run(
                    true, true,
                    new IRunnableWithProgress() {
                        public void run(final IProgressMonitor m) throws InvocationTargetException, InterruptedException {
                            synchronized (listener) {
                                listener.fProgressMonitor = m;
                                listener.fProgressMonitor.beginTask(DebugUIPlugin.removeAccelerators(getText()), listener.fRemainingUpdatesCount);
                            }

                            while ((!listener.fLabelUpdatesComplete || !listener.fViewerUpdatesComplete) && !listener.fProgressMonitor.isCanceled()) {
                                Thread.sleep(1);
                            }
                            synchronized (listener) {
                                listener.fProgressMonitor = null;
                            }
                        }
                    });
        } catch (InvocationTargetException e) {
            DebugUIPlugin.log(e);
            return;
        } catch (InterruptedException e) {
            return;
        }

        VirtualItem root = virtualViewer.getTree();
        if (!monitor.isCanceled()) {
            List list = new ArrayList();
            collectAllChildren(root, list);
            FindLabelProvider labelProvider = new FindLabelProvider(virtualViewer, list);
            VirtualItem result = performFind(list, labelProvider);
            if (result != null) {
                setSelectionToClient(virtualViewer, labelProvider, result);
            }
        }

        virtualViewer.removeLabelUpdateListener(listener);
        virtualViewer.removeViewerUpdateListener(listener);
        virtualViewer.dispose();
    }

    private int calcUpdatesCount(IModelDelta stateDelta) {
        final int[] count = new int[] { 0 };
        stateDelta.accept(new IModelDeltaVisitor() {
            public boolean visit(IModelDelta delta, int depth) {
                if ((delta.getFlags() & IModelDelta.EXPAND) != 0) {
                    count[0] += delta.getChildCount();
                    return true;
                }
                return false;
            }
        });

        // Double it to account for separate element and label update ticks.
        return count[0] * 2;
    }

    private void collectAllChildren(VirtualItem element, List collect) {
        VirtualItem[] children = element.getItems();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                if (!children[i].needsLabelUpdate()) {
                    collect.add(children[i]);
                    collectAllChildren(children[i], collect);
                }
            }
        }
    }

    protected VirtualItem performFind(List items, FindLabelProvider labelProvider) {
        FindElementDialog dialog = new FindElementDialog(
                fClientViewer.getControl().getShell(),
                labelProvider,
                items.toArray());
        dialog.setTitle(ActionMessages.FindDialog_3);
        dialog.setMessage(ActionMessages.FindDialog_1);
        if (dialog.open() == Window.OK) {
            Object[] elements = dialog.getResult();
            if (elements.length == 1) {
                return (VirtualItem) elements[0];
            }
        }
        return null;
    }

    protected void setSelectionToClient(VirtualTreeModelViewer virtualViewer, ILabelProvider labelProvider, VirtualItem findItem) {
        virtualViewer.getTree().setSelection(new VirtualItem[] { findItem });
        ModelDelta stateDelta = new ModelDelta(virtualViewer.getInput(), IModelDelta.NO_CHANGE);
        virtualViewer.saveElementState(TreePath.EMPTY, stateDelta, IModelDelta.SELECT);
        // Set the force flag to all select delta in order to override model's selection policy.
        stateDelta.accept(new IModelDeltaVisitor() {
            public boolean visit(IModelDelta delta, int depth) {
                if ((delta.getFlags() & IModelDelta.SELECT) != 0) {
                    ((ModelDelta) delta).setFlags(delta.getFlags() | IModelDelta.FORCE);
                }
                return true;
            }
        });
        fClientViewer.updateViewer(stateDelta);

        ISelection selection = fClientViewer.getSelection();
        if (!selection.isEmpty() &&
                selection instanceof IStructuredSelection &&
                ((IStructuredSelection) selection).getFirstElement().equals(findItem.getData())) {
        } else {
            DebugUIPlugin.errorDialog(
                    fClientViewer.getControl().getShell(),
                    ActionMessages.VirtualFindAction_0,
                    MessageFormat.format(ActionMessages.VirtualFindAction_1, new String[] { labelProvider.getText(findItem) }),
                    new Status(IStatus.ERROR, DebugUIPlugin.getUniqueIdentifier(), ActionMessages.VirtualFindAction_1));
        }
    }

    public void update() {
        setEnabled(fClientViewer.getInput() != null && fClientViewer.getChildCount(TreePath.EMPTY) > 0);
    }

}