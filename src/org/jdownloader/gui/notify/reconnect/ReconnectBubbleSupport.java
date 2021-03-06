package org.jdownloader.gui.notify.reconnect;

import java.util.ArrayList;
import java.util.List;

import jd.controlling.reconnect.Reconnecter;
import jd.controlling.reconnect.ReconnecterEvent;
import jd.controlling.reconnect.ReconnecterListener;

import org.jdownloader.gui.notify.AbstractBubbleSupport;
import org.jdownloader.gui.notify.BubbleNotify;
import org.jdownloader.gui.notify.BubbleNotify.AbstractNotifyWindowFactory;
import org.jdownloader.gui.notify.Element;
import org.jdownloader.gui.notify.gui.AbstractNotifyWindow;
import org.jdownloader.gui.notify.gui.CFG_BUBBLE;
import org.jdownloader.gui.translate._GUI;

public class ReconnectBubbleSupport extends AbstractBubbleSupport implements ReconnecterListener {
    
    private ArrayList<Element> elements;
    
    public ReconnectBubbleSupport() {
        super(_GUI.T.plugins_optional_JDLightTray_ballon_reconnectstart3(), CFG_BUBBLE.BUBBLE_NOTIFY_ON_RECONNECT_START_ENABLED);
        elements = new ArrayList<Element>();
        ReconnectBubbleContent.fill(elements);
        Reconnecter.getInstance().getEventSender().addListener(this, true);
    }
    
    @Override
    public void onBeforeReconnect(final ReconnecterEvent event) {
        if (isEnabled()) {
            BubbleNotify.getInstance().show(new AbstractNotifyWindowFactory() {
                
                @Override
                public AbstractNotifyWindow<?> buildAbstractNotifyWindow() {
                    return new ReconnectBubble();
                }
            });
        }
    }
    
    @Override
    public void onAfterReconnect(final ReconnecterEvent event) {
        
    }
    
    @Override
    public List<Element> getElements() {
        return elements;
    }
    
}
