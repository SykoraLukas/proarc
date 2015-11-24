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
package cz.cas.lib.proarc.common.workflow.model;

import java.sql.Timestamp;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 *
 * @author Jan Pokorsky
 */
@XmlAccessorType(XmlAccessType.NONE)
public class TaskView extends Task {

    @XmlElement(name = WorkflowModelConsts.TASK_PROFILELABEL)
    private String profileLabel;
    @XmlElement(name = WorkflowModelConsts.TASK_JOBLABEL)
    private String jobLabel;
    @XmlElement(name = WorkflowModelConsts.TASK_OWNERNAME)
    private String userName;

    public String getProfileLabel() {
        return profileLabel;
    }

    public void setProfileLabel(String profileLabel) {
        this.profileLabel = profileLabel;
    }

    public String getJobLabel() {
        return jobLabel;
    }

    public void setJobLabel(String jobLabel) {
        this.jobLabel = jobLabel;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * The conversion to and from ISO timedate loses precision of the timestamp.
     * Use this instead of {@link #getTimestamp() } for updates.
     */
    @XmlElement(name = WorkflowModelConsts.TASK_TIMESTAMP)
    public long getTimestampAsLong() {
        return getTimestamp().getTime();
    }

    @XmlElement(name = WorkflowModelConsts.TASK_MODIFIED)
    @Override
    public Timestamp getTimestamp() {
        return super.getTimestamp();
    }

}
