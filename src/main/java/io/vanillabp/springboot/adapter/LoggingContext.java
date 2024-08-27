package io.vanillabp.springboot.adapter;

import java.util.HashMap;
import java.util.Map;

public abstract class LoggingContext {

    /**
     * The current workflow module's ID.
     */
    public static final String WORKFLOW_MODULE_ID = "workflowModuleId";

    /**
     * The current VanillaBP adapter's ID.
     */
    public static final String WORKFLOW_ADAPTER_ID = "workflowAdapterId";

    /**
     * The current aggregate's ID - may be null if not yet filled by the database for auto-increments.
     */
    public static final String WORKFLOW_AGGREGATE_ID = "workflowAggregateId";

    /**
     * The current workflow's BPMN process ID (&quot;id&quot; attribute of BPMN &quot;process&quot; tag)
     * regardless whether the current action belongs to a call-activity's BPMN task. Secondary BPMN process IDs
     * are not available for logging.
     *
     * #see <a href="https://github.com/vanillabp/spi-for-java#call-activities">Call-activities</a>
     */
    public static final String WORKFLOW_BPMN_ID = "workflowBpmnId";

    /**
     * The current workflow's ID, specific to underlying BPM system (aka process instance ID)
     * - if already known by the adapter.
     */
    public static final String WORKFLOW_BPM_ID = "workflowBpmId";

    /**
     * The current workflow task's ID.
     */
    public static final String WORKFLOW_TASK_ID = "workflowTaskId";

    /**
     * The current workflow task's BPMN node (&quot;id&quot; attribute of the BPMN XML tag in combination with
     * the BPMN process ID the task belongs to - e.g. &quot;MyProcess#MyTask&quot;).
     */
    public static final String WORKFLOW_TASK_NODE = "workflowTaskNode";

    /**
     * The current workflow task's BPMN node ID (aka flow node instance ID).
     */
    public static final String WORKFLOW_TASK_NODE_ID = "workflowTaskNodeId";


    private static ThreadLocal<Map<String, Object>> context = ThreadLocal.withInitial(HashMap::new);

    /**
     * @return Immutable context
     */
    public static Map<String, Object> getContext() {
        return Map.copyOf(context.get());
    }

    protected static void clearContext() {
        context.get().clear();
    }

    protected static Map<String, Object> getWriteableContext() {
        return context.get();
    }

    /**
     * @see LoggingContext#WORKFLOW_AGGREGATE_ID
     */
    public static String getWorkflowAggregateId() {
        return (String) context.get().get(WORKFLOW_AGGREGATE_ID);
    }

    /**
     * @see LoggingContext#WORKFLOW_BPMN_ID
     */
    public static String getWorkflowBpmnId() {
        return (String) context.get().get(WORKFLOW_BPMN_ID);
    }

    /**
     * @see LoggingContext#WORKFLOW_BPM_ID
     */
    public static String getWorkflowBpmId() {
        return (String) context.get().get(WORKFLOW_BPM_ID);
    }

    /**
     * @see LoggingContext#WORKFLOW_MODULE_ID
     */
    public static String getWorkflowModuleId() {
        return (String) context.get().get(WORKFLOW_MODULE_ID);
    }

    /**
     * @see LoggingContext#WORKFLOW_ADAPTER_ID
     */
    public static String getWorkflowAdapterId() {
        return (String) context.get().get(WORKFLOW_ADAPTER_ID);
    }

    /**
     * @see LoggingContext#WORKFLOW_TASK_ID
     */
    public static String getWorkflowTaskId() {
        return (String) context.get().get(WORKFLOW_TASK_ID);
    }

    /**
     * @see LoggingContext#WORKFLOW_TASK_NODE
     */
    public static String getWorkflowTaskNode() {
        return (String) context.get().get(WORKFLOW_TASK_NODE);
    }

    /**
     * @see LoggingContext#WORKFLOW_TASK_NODE_ID
     */
    public static String getWorkflowTaskNodeId() {
        return (String) context.get().get(WORKFLOW_TASK_NODE_ID);
    }


}
