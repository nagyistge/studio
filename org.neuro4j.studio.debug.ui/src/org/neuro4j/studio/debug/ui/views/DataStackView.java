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

import java.lang.reflect.Field;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.debug.internal.ui.viewers.model.provisional.TreeModelViewer;
import org.eclipse.debug.internal.ui.views.variables.VariablesView;
import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.neuro4j.studio.debug.ui.model.JDIStackFrameAdapter;

/**
 * View of the PDA VM data stack
 */
public class DataStackView extends VariablesView {

    @Override
    public void init(IViewSite site, IMemento memento) throws PartInitException {
        super.init(site, memento);
    }

    @Override
    protected TreeModelViewer createTreeViewer(Composite parent) {
        // TODO Auto-generated method stub
    	TreeModelViewer tree =  super.createTreeViewer(parent);

    	return tree;
    }

    @Override
    public Viewer createViewer(Composite parent) {
        // TODO Auto-generated method stub
    	Viewer viewer =  super.createViewer(parent);
    	
    	return viewer;
    }

    // @Override
    // protected String getPresentationContextId() {
    // // TODO Auto-generated method stub
    // return "org.neuro4j.studio.debug.ui.debugging";
    // }

    @Override
    protected String getPresentationContextId() {
        // TODO Auto-generated method stub
        return super.getPresentationContextId();
    }

    @Override
    public void setInitializationData(IConfigurationElement cfig,
            String propertyName, Object data) {
        // TODO Auto-generated method stub
        super.setInitializationData(cfig, propertyName, data);
    }

    @Override
    protected void setViewerInput(Object context) {

        if (context == null)
        {
            refreshDetailPaneContents();
        }

        Object current = getViewer().getInput();

        if ((current == null) && (context == null)) {
            return;
        }

        if ((current != null) && (current.equals(context))) {
            return;
        }
        showViewer();
        if (context instanceof JDIStackFrame  )
        {
            if (isValidDateFrame((JDIStackFrame)context)){
                JDIStackFrameAdapter adapter = new JDIStackFrameAdapter((JDIStackFrame) context);
                
                //BreakpointContainer adapter  = new BreakpointContainer();
                
                getViewer().setInput(adapter);                
            }

        } else {
            getViewer().setInput(context);
        }

        updateObjects();
    }

    public boolean isValidDateFrame(JDIStackFrame frame) {

        Field f;
        try {
            f = frame.getClass().getDeclaredField("fDepth");
            f.setAccessible(true);
            Integer o = (Integer) f.get(frame); // IllegalAccessException
            if (o < 0)
            {
                return false;
            }
            return o > 0;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        
        }

        return true;
    }
    

}
