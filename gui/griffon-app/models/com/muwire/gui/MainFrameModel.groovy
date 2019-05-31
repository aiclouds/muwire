package com.muwire.gui

import javax.annotation.Nonnull
import javax.inject.Inject
import javax.swing.JTable

import com.muwire.core.Core
import com.muwire.core.search.UIResultEvent

import griffon.core.GriffonApplication
import griffon.core.artifact.GriffonModel
import griffon.inject.MVCMember
import griffon.transform.FXObservable
import griffon.transform.Observable
import griffon.metadata.ArtifactProviderFor

@ArtifactProviderFor(GriffonModel)
class MainFrameModel {
    @MVCMember @Nonnull
    FactoryBuilderSupport builder
    @Inject @Nonnull GriffonApplication application
    @Observable boolean coreInitialized = false
    
    @Observable def results = []
    @Observable def downloads = []

    private volatile Core core    
    
    void mvcGroupInit(Map<String, Object> args) {
        application.addPropertyChangeListener("core", {e ->
            coreInitialized = (e.getNewValue() != null)
            core = e.getNewValue()
            core.eventBus.register(UIResultEvent.class, this)
        })
    }
    
    void onUIResultEvent(UIResultEvent e) {
        runInsideUIAsync {
            results << e
            JTable table = builder.getVariable("results-table")
            table.model.fireTableDataChanged()
        }
    }
}