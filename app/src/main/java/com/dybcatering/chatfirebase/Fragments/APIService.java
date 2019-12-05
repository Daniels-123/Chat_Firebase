package com.dybcatering.chatfirebase.Fragments;

import com.dybcatering.chatfirebase.Notifications.MyResponse;
import com.dybcatering.chatfirebase.Notifications.Sender;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface APIService {
	@Headers(
			{
					"Content-Type:application/json",
					"Authorization:key=AAAAb-m5_F0:APA91bEZtZOAaL8YO7vy_y3PCO-Xg_0B6jfDSDRmYjbZyDtCnHOS9BdjlrBzR-aVuagDnCpEe4wWCsjyqdrh5oytC_qa6R-y7A7_4n1pwpUgJkNMbcye8KESJP0OhkyU6EKLZytrb2b0"
			}
	)

	@POST("fcm/send")
	Call<MyResponse> sendNotification(@Body Sender body);
}
