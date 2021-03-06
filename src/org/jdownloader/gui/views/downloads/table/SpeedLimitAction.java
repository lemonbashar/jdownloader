package org.jdownloader.gui.views.downloads.table;

import java.awt.event.ActionEvent;

import jd.controlling.packagecontroller.AbstractNode;

import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.jdownloader.actions.AppAction;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;

public class SpeedLimitAction extends AppAction {
    private final java.util.List<AbstractNode> inteliSelect;
    private final AbstractNode                 context;

    public SpeedLimitAction(AbstractNode contextObject, java.util.List<AbstractNode> inteliSelect) {
        setName(_GUI.T.ContextMenuFactory_createPopup_speed());
        setIconKey(IconKey.ICON_SPEED);
        this.context = contextObject;
        this.inteliSelect = inteliSelect;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Dialog.getInstance().showDialog(new SpeedLimitator(context, inteliSelect));
        } catch (DialogClosedException e1) {
            e1.printStackTrace();
        } catch (DialogCanceledException e1) {
            e1.printStackTrace();
        }
    }
}
