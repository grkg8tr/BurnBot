package com.nicknackhacks.dailyburn.api;

import java.util.Calendar;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;

import com.nicknackhacks.dailyburn.LogHelper;
import com.nicknackhacks.dailyburn.R;

public class AddBodyEntryDialog extends Dialog {

	public BodyDao bodyDao;
	private String metricIdentifier;
	private String metricUnit;
	
	public void setMetricIdentifier(String metricIdentifier) {
		this.metricIdentifier = metricIdentifier;
	}
	
	public void setMetricUnit(String metricUnit) {
		this.metricUnit = metricUnit;
	}
	
	public AddBodyEntryDialog(Context context, BodyDao bodyDao) {
		super(context);
		
		this.bodyDao = bodyDao;
		
		Calendar cal = Calendar.getInstance();
    	int cYear = cal.get(Calendar.YEAR);
    	int cMonth = cal.get(Calendar.MONTH);
    	int cDay = cal.get(Calendar.DAY_OF_MONTH);

		//final Dialog dialog = new Dialog(this);

		setContentView(R.layout.add_body_entry);
		setTitle("Entry:");

		DatePicker datePicker = (DatePicker) findViewById(R.id.DatePicker);
		datePicker.init(cYear,cMonth,cDay, null);
		setCancelable(true);
		
		((Button)findViewById(R.id.dialog_ok)).setOnClickListener(okClickListener);
		((Button)findViewById(R.id.dialog_cancel)).setOnClickListener(cancelClickListener);
	}

	private Button.OnClickListener okClickListener = new Button.OnClickListener() {

		public void onClick(View v) {
			cancel();

			String value = ((EditText) findViewById(R.id.body_entry))
					.getText().toString();
			DatePicker datePicker = (DatePicker) findViewById(R.id.DatePicker);
			try {
				bodyDao.addBodyLogEntry(metricIdentifier,
						value, metricUnit);
			} catch (Exception e) {
				LogHelper.LogE(e.getMessage(), e);
			}
		}
	};

	private Button.OnClickListener cancelClickListener = new Button.OnClickListener() {
		
		public void onClick(View v) {
			AddBodyEntryDialog.this.cancel();
		}
	};
}