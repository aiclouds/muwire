
package com.muwire.gui

import griffon.core.artifact.GriffonView
import static com.muwire.gui.Translator.trans
import griffon.inject.MVCMember
import griffon.metadata.ArtifactProviderFor

import javax.swing.JDialog
import javax.swing.SwingConstants
import javax.swing.event.ChangeListener

import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

import javax.annotation.Nonnull

@ArtifactProviderFor(GriffonView)
class WatchedDirectoryView {
    @MVCMember @Nonnull
    FactoryBuilderSupport builder
    @MVCMember @Nonnull
    WatchedDirectoryModel model
    
    def dialog
    def p
    def mainFrame
    
    def autoWatchCheckbox
    def syncIntervalField

    void initUI() {
        mainFrame = application.windowManager.findWindow("main-frame")
        dialog = new JDialog(mainFrame, trans("WATCHED_DIRECTORY_CONFIGURATION"), true)
        dialog.setResizable(false)
        
        p = builder.panel {
            borderLayout()
            panel (constraints : BorderLayout.NORTH) {
                label(trans("WATCHED_DIRECTORY_CONFIGURATION_FOR",model.directory.directory.toString()))
            }
            panel (constraints : BorderLayout.CENTER) {
                gridBagLayout()
                label(text : trans("WATCHED_DIRECTORY_AUTO"), constraints : gbc(gridx: 0, gridy : 0, anchor : GridBagConstraints.LINE_START, weightx: 100))
                autoWatchCheckbox = checkBox(selected : bind {model.autoWatch}, constraints : gbc(gridx: 1, gridy : 0, anchor : GridBagConstraints.LINE_END))
                label(text : trans("WATCHED_DIRECTORY_INTERVAL"), enabled : bind {!model.autoWatch}, constraints : gbc(gridx: 0, gridy : 1, anchor : GridBagConstraints.LINE_START, weightx: 100))
                syncIntervalField = textField(text : bind {model.syncInterval}, columns: 4, enabled : bind {!model.autoWatch}, 
                    constraints: gbc(gridx: 1, gridy : 1, anchor : GridBagConstraints.LINE_END, insets : [0,10,0,0]))
            }
            panel (constraints : BorderLayout.SOUTH) {
                button(text : trans("SAVE"), saveAction)
                button(text : trans("CANCEL"), cancelAction)
            }
        }
    }
        
    void mvcGroupInit(Map<String,String> args) {
        autoWatchCheckbox.addChangeListener({e ->
            model.autoWatch = autoWatchCheckbox.model.isSelected()
        } as ChangeListener)
        
        dialog.getContentPane().add(p)
        dialog.pack()
        dialog.setLocationRelativeTo(mainFrame)
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)
        dialog.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                mvcGroup.destroy()
            }
        })
        dialog.show()
    }
}