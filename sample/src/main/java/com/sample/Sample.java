package com.sample;

import java.net.MalformedURLException;

import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.table.query.ExecutableQuery;

public class Sample {
	public static class Attendee {
		String id;
		String firstname;
		String lastname;
	}

	public static void main(String[] args) {
		String host = "";
		String apiKey = "";
		String table = "";

		try {
			MobileServiceClient client = new MobileServiceClient(host, apiKey);

			ExecutableQuery<Attendee> query = client
					.getTable(table, Attendee.class)
					.where()
					.field("conferenceId").eq(6620)
					.and()
					.field("locale").eq("ru-RU")
					.top(1)
					.includeInlineCount();

			int count = query
					.execute()
					.blockingGet()
					.getTotalCount();

			System.out.println("Received " + count + " attendee(s)");

		} catch (MalformedURLException exception) {
			exception.printStackTrace();
		}
	}
}