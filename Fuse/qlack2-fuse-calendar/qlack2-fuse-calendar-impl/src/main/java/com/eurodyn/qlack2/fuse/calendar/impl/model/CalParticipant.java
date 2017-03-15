/*
* Copyright 2014 EUROPEAN DYNAMICS SA <info@eurodyn.com>
*
* Licensed under the EUPL, Version 1.1 only (the "License").
* You may not use this work except in compliance with the Licence.
* You may obtain a copy of the Licence at:
* https://joinup.ec.europa.eu/software/page/eupl/licence-eupl
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the Licence is distributed on an "AS IS" basis,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the Licence for the specific language governing permissions and
* limitations under the Licence.
*/
package com.eurodyn.qlack2.fuse.calendar.impl.model;
// Generated by Hibernate Tools 3.2.4.GA

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * CalParticipant generated by hbm2java
 */
@Entity
@Table(name="cal_participant"
)
public class CalParticipant  implements java.io.Serializable {


     private String id;
     private CalItem itemId;
     private String participantId;
     private short status;

    public CalParticipant() {
    }

    public CalParticipant(CalItem itemId, String participantId, short status) {
       this.itemId = itemId;
       this.participantId = participantId;
       this.status = status;
    }

		@Id
		public String getId() {
		if (this.id == null) {
             this.id = java.util.UUID.randomUUID().toString();
         }

		 return this.id;
		}

    public void setId(String id) {
        this.id = id;
    }
@ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="item_id", nullable=false)
		public CalItem getItemId() {
			return this.itemId;
		}

    public void setItemId(CalItem itemId) {
        this.itemId = itemId;
    }

    @Column(name="participant_id", nullable=false, length=36)
		public String getParticipantId() {
			return this.participantId;
		}

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    @Column(name="status", nullable=false)
		public short getStatus() {
			return this.status;
		}

    public void setStatus(short status) {
        this.status = status;
    }




}


