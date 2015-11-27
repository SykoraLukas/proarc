/*
 * Copyright (C) 2015 Jan Pokorsky
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.cas.lib.proarc.webapp.client.widget.workflow;

import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.DateTimeFormat.PredefinedFormat;
import com.google.gwt.user.client.ui.Widget;
import com.smartgwt.client.data.Criteria;
import com.smartgwt.client.data.DSCallback;
import com.smartgwt.client.data.DSRequest;
import com.smartgwt.client.data.DSResponse;
import com.smartgwt.client.data.DataSource;
import com.smartgwt.client.data.Record;
import com.smartgwt.client.types.Overflow;
import com.smartgwt.client.types.TitleOrientation;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.events.ItemChangedHandler;
import com.smartgwt.client.widgets.form.fields.CheckboxItem;
import com.smartgwt.client.widgets.form.fields.ComboBoxItem;
import com.smartgwt.client.widgets.form.fields.DateTimeItem;
import com.smartgwt.client.widgets.form.fields.FormItem;
import com.smartgwt.client.widgets.form.fields.LinkItem;
import com.smartgwt.client.widgets.form.fields.SelectItem;
import com.smartgwt.client.widgets.form.fields.TextAreaItem;
import com.smartgwt.client.widgets.form.fields.TextItem;
import com.smartgwt.client.widgets.form.fields.events.ClickEvent;
import com.smartgwt.client.widgets.form.fields.events.ClickHandler;
import com.smartgwt.client.widgets.form.validator.IsFloatValidator;
import com.smartgwt.client.widgets.layout.VLayout;
import cz.cas.lib.proarc.common.workflow.model.ValueType;
import cz.cas.lib.proarc.common.workflow.model.WorkflowModelConsts;
import cz.cas.lib.proarc.common.workflow.profile.DisplayType;
import cz.cas.lib.proarc.webapp.client.ClientMessages;
import cz.cas.lib.proarc.webapp.client.Editor;
import cz.cas.lib.proarc.webapp.client.ErrorHandler;
import cz.cas.lib.proarc.webapp.client.ds.RestConfig;
import cz.cas.lib.proarc.webapp.client.ds.UserDataSource;
import cz.cas.lib.proarc.webapp.client.ds.ValueMapDataSource;
import cz.cas.lib.proarc.webapp.client.ds.WorkflowParameterDataSource;
import cz.cas.lib.proarc.webapp.client.ds.WorkflowTaskDataSource;
import cz.cas.lib.proarc.webapp.client.presenter.WorkflowTasksEditor;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author Jan Pokorsky
 */
public class WorkflowTaskFormView {

    private final ClientMessages i18n;
    private final Canvas widget;
    private DynamicForm taskForm;
    private WorkflowMaterialView materialView;
    private final WorkflowTasksEditor handler;
    private DynamicForm paramForm;
    private VLayout paramContainer;
    private ItemChangedHandler itemChangedHandler;

    public WorkflowTaskFormView(ClientMessages i18n, WorkflowTasksEditor handler) {
        this.i18n = i18n;
        this.handler = handler;
        this.widget = createMainLayout();
    }

    public Canvas getWidget() {
        return widget;
    }

    /** Notifies about form changes. */
    public void setItemChangedHandler(ItemChangedHandler itemChangedHandler) {
        this.itemChangedHandler = itemChangedHandler;
        taskForm.addItemChangedHandler(itemChangedHandler);
    }

    public boolean isChanged() {
        return taskForm.valuesHaveChanged() || paramForm.valuesHaveChanged();
    }

    public DynamicForm getTask() {
        return taskForm;
    }

    public boolean validate() {
        if (taskForm.validate() && paramForm.validate()) {
            Map<?,?> params = paramForm.getValues();
            for (Iterator<?> it = params.values().iterator(); it.hasNext();) {
                Object paramValue = it.next();
                if (paramValue instanceof Map) {
                    it.remove();
                }
            }
            if (params.isEmpty()) {
                taskForm.clearValue(WorkflowTaskDataSource.FIELD_PARAMETERS);
            } else {
                taskForm.setValue(WorkflowTaskDataSource.FIELD_PARAMETERS, params);
            }
            return true;
        } else {
            return false;
        }
    }

    public void setTask(Record task) {
        if (task != null) {
            String taskId = task.getAttribute(WorkflowTaskDataSource.FIELD_ID);
            taskForm.clearErrors(true);
            taskForm.fetchData(new Criteria(WorkflowTaskDataSource.FIELD_ID, taskId));
            setParameters(taskId);
            materialView.getMaterialGrid().fetchData(
                    new Criteria(WorkflowModelConsts.MATERIALFILTER_TASKID, taskId));
        } else {
            taskForm.clearValues();
            setParameters(null);
            setMaterials(new Record[0]);
        }
    }

    public void setParameters(String taskId) {
        if (taskId != null) {
            DSRequest dsRequest = new DSRequest();
            dsRequest.setWillHandleError(true);
            WorkflowParameterDataSource.getInstance().fetchData(
                    new Criteria(WorkflowModelConsts.PARAMETERPROFILE_TASKID, taskId),
                    new DSCallback() {

                @Override
                public void execute(DSResponse dsResponse, Object data, DSRequest dsRequest) {
                    if (RestConfig.isStatusOk(dsResponse)) {
                        setParameterRecords(dsResponse.getData());
                    } else {
                        setParameterRecords(new Record[0]);
                        ErrorHandler.warn(dsResponse, dsRequest);
                    }
                }
            }, dsRequest);
        } else {
            setParameterRecords(new Record[0]);
        }
    }

    private void setMaterials(Record[] records) {
        materialView.getMaterialGrid().setData(records);
    }

    private Canvas createMainLayout() {
        VLayout forms = new VLayout();
        forms.setOverflow(Overflow.AUTO);
        forms.addMember(createForm());
        forms.addMember(createParameterList());

        VLayout main = new VLayout();
        main.addMember(forms);
        main.addMember(createMaterialList());
        return main;
    }

    private Widget createForm() {
        taskForm = new DynamicForm();
        taskForm.setDataSource(WorkflowTaskDataSource.getInstance());
        taskForm.setNumCols(3);
        taskForm.setColWidths("*", "*", "*");
        taskForm.setTitleOrientation(TitleOrientation.TOP);

//            StaticTextItem jobLabel = new StaticTextItem(WorkflowTaskDataSource.FIELD_JOB_LABEL);
        LinkItem jobLabel = new LinkItem(WorkflowTaskDataSource.FIELD_JOB_LABEL);
        jobLabel.setColSpan("*");
        jobLabel.setWidth("*");
        jobLabel.setShowTitle(false);
        jobLabel.setTextBoxStyle(Editor.CSS_PANEL_DESCRIPTION_TITLE);
        jobLabel.setReadOnlyTextBoxStyle(Editor.CSS_PANEL_DESCRIPTION_TITLE);
        jobLabel.setTarget("javascript");
        jobLabel.setTooltip("Kliknutím přejdete na záměr.");
        jobLabel.addClickHandler(new ClickHandler() {

            @Override
            public void onClick(ClickEvent event) {
                String jobId = taskForm.getValueAsString(WorkflowTaskDataSource.FIELD_JOB_ID);
                handler.onOpenJob(jobId);
            }
        });

        SelectItem owner = new SelectItem(WorkflowTaskDataSource.FIELD_OWNER);
        owner.setOptionDataSource(UserDataSource.getInstance());
        owner.setValueField(UserDataSource.FIELD_ID);
        owner.setDisplayField(UserDataSource.FIELD_USERNAME);
        owner.setWidth("*");

        TextAreaItem note = new TextAreaItem(WorkflowTaskDataSource.FIELD_NOTE);
        note.setStartRow(true);
        note.setColSpan("*");
        note.setWidth("*");
        note.setHeight(40);

        TextItem label = new TextItem(WorkflowTaskDataSource.FIELD_LABEL);
        label.setWidth("*");

        taskForm.setFields(jobLabel,
                label,
                owner,
                new SelectItem(WorkflowTaskDataSource.FIELD_STATE),
                new TextItem(WorkflowTaskDataSource.FIELD_CREATED),
                new TextItem(WorkflowTaskDataSource.FIELD_MODIFIED),
                new SelectItem(WorkflowTaskDataSource.FIELD_PRIORITY),
                note);
        return taskForm;
    }

    private DynamicForm createDefaultParameterForm() {
        DynamicForm df = new DynamicForm();
//            StaticTextItem msg = new StaticTextItem();
//            msg.setShowTitle(false);
//            msg.setValue("No parameter!");
//            df.setItems(msg);
        return df;
    }

    private DynamicForm createParameterForm(Record[] records) {
        if (records == null || records.length == 0) {
            return createDefaultParameterForm();
        }
        DynamicForm df = new DynamicForm();
        df.setUseFlatFields(true);
        df.setWrapItemTitles(false);
        df.setTitleOrientation(TitleOrientation.TOP);
        df.setNumCols(2);
        FormItem[] items = new FormItem[records.length];
        Record values = new Record();
        for (int i = 0; i < records.length; i++) {
            Record record = records[i];
            ValueType valueType = ValueType.fromString(
                    record.getAttribute(WorkflowModelConsts.PARAMETER_VALUETYPE));
            DisplayType displayType = DisplayType.fromString(
                    record.getAttribute(WorkflowModelConsts.PARAMETER_DISPLAYTYPE));
            displayType = valueType == ValueType.DATETIME ? DisplayType.DATETIME : displayType;

            String paramName = record.getAttribute(WorkflowParameterDataSource.FIELD_NAME);
            String fieldName = "f" + i;
            items[i] = createFormItem(record, valueType, displayType);
            items[i].setName(fieldName);
            // use dataPath to solve cases here the valid JSON name is not a valid javascript ID (param.id).
            items[i].setDataPath("/" + paramName);
            items[i].setTitle(record.getAttribute(WorkflowModelConsts.PARAMETER_PROFILELABEL));
            Object val = getParameterValue(record, valueType, displayType);
            if (val != null) {
                values.setAttribute(paramName, val);
            }
        }
        df.setItems(items);
        df.editRecord(values);
        df.addItemChangedHandler(itemChangedHandler);
        return df;
    }

    private Object getParameterValue(Record record, ValueType valueType, DisplayType displayType) {
        Object val = record.getAttributeAsObject(WorkflowParameterDataSource.FIELD_VALUE);
        if (valueType == ValueType.DATETIME && val instanceof String) {
            DateTimeFormat format = DateTimeFormat.getFormat(PredefinedFormat.ISO_8601);
            val = format.parse((String) val);
        } else if (displayType == DisplayType.CHECKBOX && val instanceof String) {
            if (Boolean.TRUE.toString().equalsIgnoreCase((String) val)) {
                val = true;
            } else if (Boolean.FALSE.toString().equalsIgnoreCase((String) val)) {
                val = false;
            } else {
                try {
                    val = new BigDecimal((String) val).compareTo(BigDecimal.ZERO) > 0;
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        } else if (displayType == DisplayType.CHECKBOX && val instanceof Number) {
            val = ((Number) val).doubleValue() > 0;
        }
        return val;
    }

    private Widget createParameterList() {
        paramContainer = new VLayout();
        paramContainer.setAutoHeight();
        setParameterRecords(null);
        return paramContainer;
    }

    private void setParameterRecords(Record[] records) {
        if (paramForm != null) {
            paramForm.markForDestroy();
        }
        paramForm = createParameterForm(records);
        paramContainer.setMembers(paramForm);
    }

    private FormItem createFormItem(Record editedRecord, ValueType valueType, DisplayType displayType) {
        FormItem fi = createFormItem(displayType, editedRecord);

        fi.setRequired(editedRecord.getAttributeAsBoolean(WorkflowModelConsts.PARAMETER_REQUIRED));
        if (valueType == ValueType.NUMBER && displayType != DisplayType.CHECKBOX) {
            fi.setValidators(new IsFloatValidator());
        }
        return fi;
    }

    private FormItem createFormItem(DisplayType displayType, Record profile) {
        String name = profile.getAttribute(WorkflowParameterDataSource.FIELD_NAME);
        switch (displayType) {
            case SELECT:
                SelectItem si = new SelectItem();
                setOptions(si, profile);
                return si;
            case COMBOBOX:
                ComboBoxItem cbi = new ComboBoxItem();
                setOptions(cbi, profile);
                cbi.setLength(2000);
                return cbi;
            case CHECKBOX:
                CheckboxItem ci = new CheckboxItem();
                // the width must be set otherwise it overflows the form
                ci.setWidth(150);
                ci.setAllowEmptyValue(true);
                return ci;
            case TEXTAREA:
                TextAreaItem tai = new TextAreaItem();
                tai.setStartRow(true);
                tai.setEndRow(true);
                tai.setLength(2000);
                tai.setColSpan("*");
                tai.setWidth("*");
                tai.setHeight(30);
                return tai;
            case DATETIME:
                DateTimeItem di = new DateTimeItem();
                return di;
            case TEXT:
            default:
                TextItem ti = new TextItem(name);
                ti.setLength(2000);
                return ti;
        }
    }

    private void setOptions(FormItem item, Record profile) {
        String dataSourceId = profile.getAttribute(WorkflowModelConsts.PARAMETER_VALUEMAPID);
        if (dataSourceId != null) {
            DataSource ds = ValueMapDataSource.getInstance().getOptionDataSource(dataSourceId);
            item.setValueField(profile.getAttribute(WorkflowModelConsts.PARAMETER_OPTION_VALUE_FIELD));
            item.setOptionDataSource(ds);
            item.setDisplayField(profile.getAttribute(WorkflowModelConsts.PARAMETER_OPTION_DISPLAY_FIELD));
        }
    }

    private Widget createMaterialList() {
        materialView = new WorkflowMaterialView(i18n);
        return materialView.getWidget();
    }

}
