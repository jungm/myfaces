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
package org.apache.myfaces.renderkit.html.base;

import org.apache.myfaces.renderkit.html.util.HtmlRendererUtils;
import org.apache.myfaces.renderkit.html.util.CommonHtmlAttributesUtil;
import org.apache.myfaces.renderkit.html.util.CommonHtmlEventsUtil;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIMessage;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.component.behavior.ClientBehavior;
import jakarta.faces.component.behavior.ClientBehaviorHolder;
import jakarta.faces.component.html.HtmlMessage;
import jakarta.faces.component.search.SearchExpressionContext;
import jakarta.faces.component.search.SearchExpressionHandler;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.ResponseWriter;
import org.apache.myfaces.component.search.MyFacesSearchExpressionHints;
import org.apache.myfaces.core.api.shared.AttributeUtils;
import org.apache.myfaces.core.api.shared.ComponentUtils;

import org.apache.myfaces.renderkit.html.util.HTML;
import org.apache.myfaces.renderkit.html.util.ComponentAttrs;

public abstract class HtmlMessageRendererBase
        extends HtmlRenderer
{
    private static final Logger log = Logger.getLogger(HtmlMessageRendererBase.class.getName());

    protected abstract String getSummary(FacesContext facesContext,
                                         UIComponent message,
                                         FacesMessage facesMessage,
                                         String msgClientId);

    protected abstract String getDetail(FacesContext facesContext,
                                        UIComponent message,
                                        FacesMessage facesMessage,
                                        String msgClientId);


    protected void renderMessage(FacesContext facesContext,
                                 UIComponent message)
            throws IOException
    {
        renderMessage(facesContext, message, false);
    }

    protected void renderMessage(FacesContext facesContext, UIComponent message, 
            boolean alwaysRenderSpan) throws IOException
    {
        renderMessage(facesContext, message, alwaysRenderSpan, false);
    }
    
    /**
     * @param facesContext
     * @param message
     * @param alwaysRenderSpan if true will render a span even if there is no message
     */
    protected void renderMessage(FacesContext facesContext, UIComponent message, 
            boolean alwaysRenderSpan, boolean renderDivWhenNoMessagesAndIdSet) throws IOException
    {
        String forAttr = getFor(message);
        if (forAttr == null)
        {
            log.severe("Attribute 'for' of UIMessage "
                    + ComponentUtils.getPathToComponent(message) + " must not be null");
            return;
        }

        SearchExpressionHandler searchExpressionHandler = facesContext.getApplication().getSearchExpressionHandler();

        String clientId = searchExpressionHandler.resolveClientId(
                SearchExpressionContext.createSearchExpressionContext(
                        facesContext, message, MyFacesSearchExpressionHints.SET_IGNORE_NO_RESULT, null), forAttr);
        if (clientId == null)
        {
            log.severe("Could not render Message. Unable to find component '" 
                    + forAttr + "' (calling findComponent on component '" 
                    + message.getClientId(facesContext) 
                    + "'). If the provided id was correct, wrap the message and its " 
                    + "component into an h:panelGroup or h:panelGrid.");
            return;
        }

        Iterator<FacesMessage> messageIterator = facesContext.getMessages(clientId);
        if (!messageIterator.hasNext())
        {
            // No associated message, nothing to render
            if (alwaysRenderSpan)
            {
                ResponseWriter writer = facesContext.getResponseWriter();
                writer.startElement(HTML.SPAN_ELEM, message);
                writer.writeAttribute(HTML.ID_ATTR, clientId + "_msgFor", null);
                HtmlRendererUtils.renderHTMLStringAttribute(writer, message, ComponentAttrs.STYLE_ATTR,
                        HTML.STYLE_ATTR);
                HtmlRendererUtils.renderHTMLStringAttribute(writer, message, ComponentAttrs.STYLE_CLASS_ATTR,
                        HTML.CLASS_ATTR);
                writer.endElement(HTML.SPAN_ELEM);
            }
            else if (renderDivWhenNoMessagesAndIdSet && shouldRenderId(facesContext, message))
            {
                // show span anyways in case there's a client side update, ie: ajax
                ResponseWriter writer = facesContext.getResponseWriter();
                writer.startElement(HTML.SPAN_ELEM, message);
                writer.writeAttribute(HTML.ID_ATTR, message.getClientId(facesContext), null);
                writer.endElement(HTML.SPAN_ELEM);
            }
            return;
        }
        
        // get first message
        FacesMessage facesMessage = messageIterator.next();
        
        // check for the redisplay attribute and for the messages which have already been rendered
        if(!isRedisplay(message)) 
        {
            while(facesMessage.isRendered())
            {
                if(messageIterator.hasNext()) 
                {
                    // get the next message
                    facesMessage = messageIterator.next();
                }
                else 
                {
                    // no more message to be rendered
                    return; 
                }
            }
        }

        // and render it
        renderSingleFacesMessage(facesContext, message, facesMessage, clientId);
    }


    protected void renderSingleFacesMessage(FacesContext facesContext,
                                            UIComponent message,
                                            FacesMessage facesMessage,
                                            String messageClientId)
            throws IOException
    {
        renderSingleFacesMessage(facesContext, message, facesMessage, messageClientId,true);
    }
    
    protected void renderSingleFacesMessage(FacesContext facesContext,
            UIComponent message,
            FacesMessage facesMessage,
            String messageClientId,
            boolean renderId)
    throws IOException
    {
        renderSingleFacesMessage(facesContext, message, facesMessage, messageClientId, renderId, true);
    }

    protected void renderSingleFacesMessage(FacesContext facesContext,
            UIComponent message,
            FacesMessage facesMessage,
            String messageClientId,
            boolean renderId,
            boolean renderStyleAndStyleClass)
    throws IOException
    {
        Map<String, List<ClientBehavior>> behaviors = null;
        if (message instanceof ClientBehaviorHolder)
        {
            behaviors = ((ClientBehaviorHolder) message).getClientBehaviors();
        }
        boolean wrapSpan = (message.getId() != null && !message.getId().startsWith(UIViewRoot.UNIQUE_ID_PREFIX)) 
            || (behaviors != null && !behaviors.isEmpty());

        renderSingleFacesMessage(facesContext, message, facesMessage, messageClientId, 
                renderId, renderStyleAndStyleClass, wrapSpan);
    }
    
    protected void renderSingleFacesMessage(FacesContext facesContext,
                                            UIComponent message,
                                            FacesMessage facesMessage,
                                            String messageClientId,
                                            boolean renderId,
                                            boolean renderStyleAndStyleClass,
                                            boolean wrapSpan)
            throws IOException
    {
        // determine style and style class
        String[] styleAndClass = HtmlMessageRendererBase.getStyleAndStyleClass(
                message, facesMessage.getSeverity());
        String style = styleAndClass[0];
        String styleClass = styleAndClass[1];

        String summary = getSummary(facesContext, message, facesMessage, messageClientId);
        String detail = getDetail(facesContext, message, facesMessage, messageClientId);

        String title = getTitle(message);
        boolean tooltip = isTooltip(message);

        boolean showSummary = isShowSummary(message) && (summary != null);
        boolean showDetail = isShowDetail(message) && (detail != null);
        
        if (title == null && tooltip)
        {
            if (showDetail)
            {
                title = detail;
            }
            else if (detail != null)
            {
                title = detail;
            }
            else
            {
                title = summary;
            }
        }

        ResponseWriter writer = facesContext.getResponseWriter();

        boolean span = false;

        Map<String, List<ClientBehavior>> behaviors = null;
        if (message instanceof ClientBehaviorHolder)
        {
            behaviors = ((ClientBehaviorHolder) message).getClientBehaviors();
            // If there is a behavior registered, force wrapSpan
            wrapSpan = wrapSpan || !behaviors.isEmpty();
        }
        
        if (wrapSpan)
        {
            span = true;

            writer.startElement(HTML.SPAN_ELEM, message);

            if (behaviors != null && !behaviors.isEmpty())
            {
                //force id rendering, because the client behavior could require it
                writer.writeAttribute(HTML.ID_ATTR, message.getClientId(facesContext),null);
            }
            else if (renderId)
            {
                HtmlRendererUtils.writeIdIfNecessary(writer, message, facesContext);
            }
            if (message instanceof ClientBehaviorHolder)
            {
                behaviors = ((ClientBehaviorHolder) message).getClientBehaviors();
                if (behaviors.isEmpty() && isCommonPropertiesOptimizationEnabled(facesContext))
                {
                    CommonHtmlAttributesUtil.renderEventProperties(writer, 
                            CommonHtmlAttributesUtil.getMarkedAttributes(message), message);
                }
                else
                {
                    if (isCommonEventsOptimizationEnabled(facesContext))
                    {
                        CommonHtmlEventsUtil.renderBehaviorizedEventHandlers(facesContext, writer, 
                               CommonHtmlAttributesUtil.getMarkedAttributes(message),
                               CommonHtmlEventsUtil.getMarkedEvents(message), message, behaviors);
                    }
                    else
                    {
                        HtmlRendererUtils.renderBehaviorizedEventHandlers(facesContext, writer, message, behaviors);
                    }
                }
                HtmlRendererUtils.renderHTMLAttributes(writer, message, 
                        HTML.UNIVERSAL_ATTRIBUTES_WITHOUT_STYLE_AND_TITLE);
            }
            else
            {
                HtmlRendererUtils.renderHTMLAttributes(writer, message, 
                        HTML.MESSAGE_PASSTHROUGH_ATTRIBUTES_WITHOUT_TITLE_STYLE_AND_STYLE_CLASS);
            }
        }
        else
        {
            span = HtmlRendererUtils.renderHTMLAttributesWithOptionalStartElement(
                    writer, message, HTML.SPAN_ELEM, 
                    HTML.MESSAGE_PASSTHROUGH_ATTRIBUTES_WITHOUT_TITLE_STYLE_AND_STYLE_CLASS);
        }

        span |= HtmlRendererUtils.renderHTMLAttributeWithOptionalStartElement(
                writer, message, HTML.SPAN_ELEM, HTML.TITLE_ATTR, title, span);
        if (renderStyleAndStyleClass)
        {
            span |= HtmlRendererUtils.renderHTMLAttributeWithOptionalStartElement(
                    writer, message, HTML.SPAN_ELEM, HTML.STYLE_ATTR, style, span);
            span |= HtmlRendererUtils.renderHTMLAttributeWithOptionalStartElement(
                    writer, message, HTML.SPAN_ELEM, HTML.STYLE_CLASS_ATTR, styleClass, span);
            // Remember if renderStyleAndStyleClass is true, it means style and styleClass
            // are rendered on the outer tag, and in that sense, role attribute should
            // be rendered there too.
            span |= HtmlRendererUtils.renderHTMLAttributeWithOptionalStartElement(
                    writer, message, HTML.ROLE_ATTR, HTML.ROLE_ATTR, 
                    message.getAttributes().get(HTML.ROLE_ATTR), span);
        }

        if (showSummary && !(title == null && tooltip))
        {
            writer.writeText(summary, null);
            if (showDetail)
            {
                writer.writeText(" ", null);
            }
        }

        if (showDetail)
        {
            writer.writeText(detail, null);
        }

        if (span)
        {
            writer.endElement(HTML.SPAN_ELEM);
        }
        
        // note that this FacesMessage already has been rendered 
        facesMessage.rendered();
    }


    public static String[] getStyleAndStyleClass(UIComponent message,
                                                 FacesMessage.Severity severity)
    {
        String style = null;
        String styleClass = null;
        if (message instanceof HtmlMessage)
        {
            if (severity == FacesMessage.SEVERITY_INFO)
            {
                style = ((HtmlMessage) message).getInfoStyle();
                styleClass = ((HtmlMessage) message).getInfoClass();
            }
            else if (severity == FacesMessage.SEVERITY_WARN)
            {
                style = ((HtmlMessage) message).getWarnStyle();
                styleClass = ((HtmlMessage) message).getWarnClass();
            }
            else if (severity == FacesMessage.SEVERITY_ERROR)
            {
                style = ((HtmlMessage) message).getErrorStyle();
                styleClass = ((HtmlMessage) message).getErrorClass();
            }
            else if (severity == FacesMessage.SEVERITY_FATAL)
            {
                style = ((HtmlMessage) message).getFatalStyle();
                styleClass = ((HtmlMessage) message).getFatalClass();
            }

            if (style == null)
            {
                style = ((HtmlMessage) message).getStyle();
            }

            if (styleClass == null)
            {
                styleClass = ((HtmlMessage) message).getStyleClass();
            }
        }
        else
        {
            Map<String, Object> attr = message.getAttributes();
            if (severity == FacesMessage.SEVERITY_INFO)
            {
                style = (String) attr.get(ComponentAttrs.INFO_STYLE_ATTR);
                styleClass = (String) attr.get(ComponentAttrs.INFO_CLASS_ATTR);
            }
            else if (severity == FacesMessage.SEVERITY_WARN)
            {
                style = (String) attr.get(ComponentAttrs.WARN_STYLE_ATTR);
                styleClass = (String) attr.get(ComponentAttrs.WARN_CLASS_ATTR);
            }
            else if (severity == FacesMessage.SEVERITY_ERROR)
            {
                style = (String) attr.get(ComponentAttrs.ERROR_STYLE_ATTR);
                styleClass = (String) attr.get(ComponentAttrs.ERROR_CLASS_ATTR);
            }
            else if (severity == FacesMessage.SEVERITY_FATAL)
            {
                style = (String) attr.get(ComponentAttrs.FATAL_STYLE_ATTR);
                styleClass = (String) attr.get(ComponentAttrs.FATAL_CLASS_ATTR);
            }

            if (style == null)
            {
                style = (String) attr.get(ComponentAttrs.STYLE_ATTR);
            }

            if (styleClass == null)
            {
                styleClass = (String) attr.get(ComponentAttrs.STYLE_CLASS_ATTR);
            }
        }

        return new String[]{style, styleClass};
    }

    protected String getFor(UIComponent component)
    {
        if (component instanceof UIMessage)
        {
            return ((UIMessage) component).getFor();
        }
 
        return (String) component.getAttributes().get(ComponentAttrs.FOR_ATTR);
        
    }

    protected String getTitle(UIComponent component)
    {
        if (component instanceof HtmlMessage)
        {
            return ((HtmlMessage) component).getTitle();
        }

        return (String) component.getAttributes().get(ComponentAttrs.TITLE_ATTR);
        
    }

    protected boolean isTooltip(UIComponent component)
    {
        if (component instanceof HtmlMessage)
        {
            return ((HtmlMessage) component).isTooltip();
        }

        return AttributeUtils.getBooleanAttribute(component, ComponentAttrs.TOOLTIP_ATTR, false);
    }

    protected boolean isShowSummary(UIComponent component)
    {
        if (component instanceof UIMessage)
        {
            return ((UIMessage) component).isShowSummary();
        }

        return AttributeUtils.getBooleanAttribute(component, ComponentAttrs.SHOW_SUMMARY_ATTR, false);
    }

    protected boolean isShowDetail(UIComponent component)
    {
        if (component instanceof UIMessage)
        {
            return ((UIMessage) component).isShowDetail();
        }

        return AttributeUtils.getBooleanAttribute(component, ComponentAttrs.SHOW_DETAIL_ATTR, false);
    }
    
    protected boolean isRedisplay(UIComponent component)
    {
        if (component instanceof UIMessage)
        {
            return ((UIMessage) component).isRedisplay();
        }

        return AttributeUtils.getBooleanAttribute(component, ComponentAttrs.REDISPLAY_ATTR, true);
    }

}
