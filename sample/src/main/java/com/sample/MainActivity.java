package com.sample;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;

import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.table.query.ExecutableQuery;

public class MainActivity extends Activity {
	public static class Attendee {
		private String id;
		private String firstname;
		private String lastname;
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			MobileServiceClient client = new MobileServiceClient(BuildConfig.AZURE_HOST, BuildConfig.AZURE_API_KEY);

			ExecutableQuery<Attendee> query = client
					.getTable(BuildConfig.AZURE_TABLE, Attendee.class)
					.where()
					.field("conferenceId").eq(6620)
					.and()
					.field("locale").eq("ru-RU")
					.top(1)
					.includeInlineCount();

			int count = query
					.execute()
					.get()
					.getTotalCount();

			System.out.println("Received " + count + " attendee(s)");

		} catch (MalformedURLException | InterruptedException | ExecutionException exception) {
			exception.printStackTrace();
		}
	}
}
