package com.eurodyn.qlack2.be.forms.api.request.form;

import com.eurodyn.qlack2.fuse.idm.api.signing.QSignedRequest;

public class DeleteFormRequest extends QSignedRequest {
	private String formId;

	public String getFormId() {
		return formId;
	}

	public void setFormId(String formId) {
		this.formId = formId;
	}

}
