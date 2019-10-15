/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.myfaces.view.facelets.tag.ui;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import javax.el.ELException;
import javax.el.ValueExpression;
import javax.el.VariableMapper;
import javax.faces.FacesException;
import javax.faces.application.ProjectStage;
import javax.faces.application.StateManager;
import javax.faces.component.UIComponent;
import javax.faces.event.PhaseId;
import javax.faces.view.facelets.FaceletContext;
import javax.faces.view.facelets.FaceletException;
import javax.faces.view.facelets.TagAttribute;
import javax.faces.view.facelets.TagConfig;
import javax.faces.view.facelets.TagHandler;

import org.apache.myfaces.util.lang.ClassUtils;
import org.apache.myfaces.view.facelets.AbstractFaceletContext;
import org.apache.myfaces.view.facelets.FaceletCompositionContext;
import org.apache.myfaces.view.facelets.el.VariableMapperWrapper;
import org.apache.myfaces.view.facelets.tag.ComponentContainerHandler;
import org.apache.myfaces.view.facelets.tag.TagHandlerUtils;
import org.apache.myfaces.view.facelets.tag.jsf.ComponentSupport;

/**
 * The include tag can point at any Facelet which might use the composition tag,
 * component tag, or simply be straight XHTML/XML. It should be noted that the 
 * src path does allow relative path names, but they will always be resolved 
 * against the original Facelet requested. 
 * 
 * The include tag can be used in conjunction with multiple &lt;ui:param/&gt; 
 * tags to pass EL expressions/values to the target page.
 * 
 * NOTE: This implementation is provided for compatibility reasons and
 * it is considered faulty. It is enabled using
 * org.apache.myfaces.STRICT_JSF_2_FACELETS_COMPATIBILITY web config param.
 * Don't use it if EL expression caching is enabled.
 * 
 * @author Jacob Hookom
 * @version $Id$
 */
public final class LegacyIncludeHandler extends TagHandler implements ComponentContainerHandler
{

    private static final String ERROR_PAGE_INCLUDE_PATH = "javax.faces.error.xhtml";
    private static final String ERROR_FACELET = "META-INF/rsc/myfaces-dev-error-include.xhtml";
    
    /**
     * A literal or EL expression that specifies the target Facelet that you 
     * would like to include into your document.
     */
    private final TagAttribute src;
    
    private final LegacyParamHandler[] _params;

    public LegacyIncludeHandler(TagConfig config)
    {
        super(config);
        this.src = this.getRequiredAttribute("src");
        
        ArrayList<LegacyParamHandler> params = TagHandlerUtils.findNextByType(nextHandler, 
                LegacyParamHandler.class);
        if (params.isEmpty())
        {
            _params = null;
        }
        else
        {
            _params = (LegacyParamHandler[]) params.toArray(new LegacyParamHandler[params.size()]);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.faces.view.facelets.FaceletHandler#apply(javax.faces.view.facelets.FaceletContext, javax.faces.component.UIComponent)
     */
    @Override
    public void apply(FaceletContext ctx, UIComponent parent) throws IOException, FacesException, FaceletException,
            ELException
    {
        AbstractFaceletContext actx = (AbstractFaceletContext) ctx;
        FaceletCompositionContext fcc = FaceletCompositionContext.getCurrentInstance(ctx);
        String path;
        boolean markInitialState = false;
        if (!src.isLiteral())
        {
            String uniqueId = actx.generateUniqueFaceletTagId(fcc.startComponentUniqueIdSection(), tagId);

            String restoredPath = (String) ComponentSupport.restoreInitialTagState(ctx, fcc, parent, uniqueId);
            if (restoredPath != null)
            {
                // If is not restore view phase, the path value should be
                // evaluated and if is not equals, trigger markInitialState stuff.
                if (!PhaseId.RESTORE_VIEW.equals(ctx.getFacesContext().getCurrentPhaseId()))
                {
                    path = this.src.getValue(ctx);
                    if (path == null || path.length() == 0)
                    {
                        return;
                    }
                    if (!path.equals(restoredPath))
                    {
                        markInitialState = true;
                    }
                }
                else
                {
                    path = restoredPath;
                }
            }
            else
            {
                //No state restored, calculate path
                path = this.src.getValue(ctx);
            }
            ComponentSupport.saveInitialTagState(ctx, fcc, parent, uniqueId, path);
        }
        else
        {
            path = this.src.getValue(ctx);
        }
        try
        {
            if (path == null || path.length() == 0)
            {
                return;
            }
            VariableMapper orig = ctx.getVariableMapper();
            ctx.setVariableMapper(new VariableMapperWrapper(orig));
            try
            {
                //Only ui:param could be inside ui:include.

                URL url = null;
                boolean oldMarkInitialState = false;
                Boolean isBuildingInitialState = null;
                // if we are in ProjectStage Development and the path equals "javax.faces.error.xhtml"
                // we should include the default error page
                if (ctx.getFacesContext().isProjectStage(ProjectStage.Development) 
                        && ERROR_PAGE_INCLUDE_PATH.equals(path))
                {
                    url = ClassUtils.getResource(ERROR_FACELET);
                }
                if (markInitialState)
                {
                    //set markInitialState flag
                    oldMarkInitialState = fcc.isMarkInitialState();
                    fcc.setMarkInitialState(true);
                    isBuildingInitialState = (Boolean) ctx.getFacesContext().getAttributes().put(
                            StateManager.IS_BUILDING_INITIAL_STATE, Boolean.TRUE);
                }
                try
                {
                    if (_params != null)
                    {
                        // ui:include defines a new TemplateContext, but ui:param EL expressions
                        // defined inside should be built before the new context is setup, to
                        // apply then after. The final effect is EL expressions will be resolved
                        // correctly when nested ui:params with the same name or based on other
                        // ui:params are used.
                        
                        String[] names = new String[_params.length];
                        ValueExpression[] values = new ValueExpression[_params.length];
                        
                        for (int i = 0; i < _params.length; i++)
                        {
                            names[i] = _params[i].getName(ctx);
                            values[i] = _params[i].getValue(ctx);
                        }

                        for (int i = 0; i < _params.length; i++)
                        {
                            _params[i].apply(ctx, parent, names[i], values[i]);
                        }
                    }

                    if (url == null)
                    {
                        ctx.includeFacelet(parent, path);
                    }
                    else
                    {
                        ctx.includeFacelet(parent, url);
                    }
                }
                finally
                {
                    if (markInitialState)
                    {
                        //unset markInitialState flag
                        if (isBuildingInitialState == null)
                        {
                            ctx.getFacesContext().getAttributes().remove(
                                    StateManager.IS_BUILDING_INITIAL_STATE);
                        }
                        else
                        {
                            ctx.getFacesContext().getAttributes().put(
                                    StateManager.IS_BUILDING_INITIAL_STATE, isBuildingInitialState);
                        }
                        fcc.setMarkInitialState(oldMarkInitialState);
                    }
                }
            }
            finally
            {
                ctx.setVariableMapper(orig);
            }
        }
        finally
        {
            if (!src.isLiteral())
            {
                fcc.endComponentUniqueIdSection();
                
                if (fcc.isUsingPSSOnThisView() && fcc.isRefreshTransientBuildOnPSS() &&
                    !fcc.isRefreshingTransientBuild())
                {
                    //Mark the parent component to be saved and restored fully.
                    ComponentSupport.markComponentToRestoreFully(ctx.getFacesContext(), parent);
                }
                if (fcc.isDynamicComponentSection())
                {
                    ComponentSupport.markComponentToRefreshDynamically(ctx.getFacesContext(), parent);
                }
            }
        }
    }
}
