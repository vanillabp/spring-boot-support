package io.vanillabp.springboot.utils;

import java.util.Collection;

public class WorkflowAndModule {
    
    private String workflowModuleId;
    
    private String bpmnProcessId;
    
    private boolean noMigration;
    
    public String getBpmnProcessId() {
        return bpmnProcessId;
    }
    
    public void setBpmnProcessId(String bpmnProcessId) {
        this.bpmnProcessId = bpmnProcessId;
    }
    
    public String getWorkflowModuleId() {
        return workflowModuleId;
    }
    
    public void setWorkflowModuleId(String workflowModuleId) {
        this.workflowModuleId = workflowModuleId;
    }
    
    public boolean isNoMigration() {
        return noMigration;
    }
    
    public void setNoMigration(boolean noMigration) {
        this.noMigration = noMigration;
    }

    public boolean matches(
            final String workflowModuleId) {
        
        return matches(workflowModuleId, null);
        
    }

    public boolean matchesAny(
            final String workflowModuleId,
            final Collection<String> bpmnProcessIds) {
        
        if ((this.workflowModuleId != null)
                && (workflowModuleId != null)) {
            if (!this.workflowModuleId.equals(workflowModuleId)) {
                return false;
            }
        }
        
        if (this.bpmnProcessId == null) {
            return true;
        }
        if (bpmnProcessId == null) {
            return true;
        }
        
        return bpmnProcessIds.contains(this.bpmnProcessId);
        
    }

    public boolean matches(
            final String workflowModuleId,
            final String bpmnProcessId) {
        
        if ((this.workflowModuleId != null)
                && (workflowModuleId != null)) {
            if (!this.workflowModuleId.equals(workflowModuleId)) {
                return false;
            }
        }
        
        if (this.bpmnProcessId == null) {
            return true;
        }
        if (bpmnProcessId == null) {
            return true;
        }
        
        return this.bpmnProcessId.equals(bpmnProcessId);
        
    }
    
}
