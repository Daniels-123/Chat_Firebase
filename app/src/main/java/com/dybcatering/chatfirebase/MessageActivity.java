package com.dybcatering.chatfirebase;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


import com.bumptech.glide.Glide;
import com.dybcatering.chatfirebase.Adapter.MessageAdapter;
import com.dybcatering.chatfirebase.Fragments.APIService;
import com.dybcatering.chatfirebase.Model.Chat;
import com.dybcatering.chatfirebase.Model.User;
import com.dybcatering.chatfirebase.Notifications.Client;
import com.dybcatering.chatfirebase.Notifications.Data;
import com.dybcatering.chatfirebase.Notifications.MyResponse;
import com.dybcatering.chatfirebase.Notifications.Sender;
import com.dybcatering.chatfirebase.Notifications.Token;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessageActivity extends AppCompatActivity {

	CircleImageView profile_image;
	TextView username;

	FirebaseUser firebaseUser;
	DatabaseReference databaseReference;

	Intent intent;

	ImageButton btn_send;
	EditText text_send;

	MessageAdapter MessageAdapter;
	List<Chat> mchat;

	RecyclerView recyclerView;

	ValueEventListener seenListener;

	APIService apiService;

	boolean notify = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_message);

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setTitle("");
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(MessageActivity.this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
			}
		});


		apiService = Client.getClient("https://fcm.googleapis.com/").create(APIService.class);


		recyclerView = findViewById(R.id.recycler_view);
		recyclerView.setHasFixedSize(true);
		LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
		linearLayoutManager.setStackFromEnd(true);
		recyclerView.setLayoutManager(linearLayoutManager);


		profile_image = findViewById(R.id.profile_image);
		username = findViewById(R.id.username);
		btn_send = findViewById(R.id.btn_send);
		text_send = findViewById(R.id.text_send);
		intent = getIntent();
		final String userId = intent.getStringExtra("userid");
		firebaseUser = FirebaseAuth.getInstance().getCurrentUser();


		username.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

			}
		});


		btn_send.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				notify = true;
				String msg = text_send.getText().toString();
				if (!msg.equals("")){
					sendMessage(firebaseUser.getUid(), userId, msg);
				}else {
					Toast.makeText(MessageActivity.this, "El mensaje se encuentra vacío", Toast.LENGTH_SHORT).show();
				}
				text_send.setText("");
			}
		});

		databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(userId);

		databaseReference.addValueEventListener(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
				User user = dataSnapshot.getValue(User.class);
				assert user != null;
				username.setText(user.getUsername());
				if (user.getImageURL().equals("default")){
					profile_image.setImageResource(R.mipmap.ic_launcher);
				}else{
					Glide.with(getApplicationContext()).load(user.getImageURL()).into(profile_image);
				}

				readMessages(firebaseUser.getUid(), userId, user.getImageURL());
			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {

			}
		});
		seenMessage(userId);
	}

	public void seenMessage(final String userid){
		databaseReference = FirebaseDatabase.getInstance().getReference("Chats");
		seenListener = databaseReference.addValueEventListener(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
				for (DataSnapshot snapshot : dataSnapshot.getChildren()){
					Chat chat = snapshot.getValue(Chat.class);
					if (chat.getReceiver().equals(firebaseUser.getUid()) && chat.getSender().equals(userid)){
						HashMap<String, Object> hashMap = new HashMap<>();
						hashMap.put("isseen", true);
						snapshot.getRef().updateChildren(hashMap);
					}
				}
			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {

			}
		});
	}

	private void sendMessage(String sender, final String receiver, String message){

		DatabaseReference reference = FirebaseDatabase.getInstance().getReference();
		final String userId = intent.getStringExtra("userid");

		HashMap<String, Object> hashMap = new HashMap<>();
		hashMap.put("sender", sender);
		hashMap.put("receiver", receiver);
		hashMap.put("message", message);
		hashMap.put("isseen", false);

		reference.child("Chats").push().setValue(hashMap);


		final DatabaseReference chatRef = FirebaseDatabase.getInstance().getReference("Chatlist")
				.child(firebaseUser.getUid())
				.child(userId);

		chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
				if (!dataSnapshot.exists()){
					chatRef.child("id").setValue(userId);
				}
			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {

			}
		});


		final String msg = 	message;


		reference = FirebaseDatabase.getInstance().getReference("Users").child(firebaseUser.getUid());
		reference.addValueEventListener(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
				User user = dataSnapshot.getValue(User.class);
				if (notify){

					sendNotification(receiver, user.getUsername(), msg);
				}
				notify = false;
			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {

			}
		});

	}


	private void sendNotification(String receiver, final String username, final String message){
		final String userId = intent.getStringExtra("userid");
		DatabaseReference tokens = FirebaseDatabase.getInstance().getReference("Tokens");
		Query query = tokens.orderByKey().equalTo(receiver);
		query.addValueEventListener(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
				for (DataSnapshot dataSnapshot1 : dataSnapshot.getChildren()){
					Token token = dataSnapshot1.getValue(Token.class);
					Data data = new Data(firebaseUser.getUid(), R.mipmap.ic_launcher, username+": "+ message, "Nuevo Mensaje"
											, userId);

					Sender sender = new Sender(data, token.getToken());

					apiService.sendNotification(sender)
							.enqueue(new Callback<MyResponse>() {
								@Override
								public void onResponse(Call<MyResponse> call, Response<MyResponse> response) {
										if (response.code() == 200){
											if (response.body().success != 1){
												Toast.makeText(MessageActivity.this, "Algo falló", Toast.LENGTH_SHORT).show();
											}
										}
								}

								@Override
								public void onFailure(Call<MyResponse> call, Throwable t) {

								}
							});
				}
			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {

			}
		});
	}

	private void readMessages(final String myid, final String userid, final String imageurl){
		mchat= new ArrayList<>();

		databaseReference = FirebaseDatabase.getInstance().getReference("Chats");
		databaseReference.addValueEventListener(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
				mchat.clear();
				for (DataSnapshot snapshot : dataSnapshot.getChildren()){
					Chat chat = snapshot.getValue(Chat.class);
					if (chat.getReceiver().equals(myid) && chat.getSender().equals(userid) ||
							chat.getReceiver().equals(userid) && chat.getSender().equals(myid)){
						mchat.add(chat);
					}

					MessageAdapter = new MessageAdapter(MessageActivity.this, 	mchat, imageurl );
					recyclerView.setAdapter(MessageAdapter);
				}

			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {

			}
		});

	}


	private void currentUser(String userid){
		SharedPreferences.Editor editor = getSharedPreferences("PREFS", MODE_PRIVATE).edit();
		editor.putString("currentuser", userid);
		editor.apply();
	}


	private void status(String status){

		databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(firebaseUser.getUid());

		HashMap<String , Object> hashMap = new HashMap<>();
		hashMap.put("status", status);

		databaseReference.updateChildren(hashMap);
	}


	@Override
	protected void onResume() {
		super.onResume();
		status("Conectado");
		final String userId = intent.getStringExtra("userid");
		currentUser(userId);
	}

	@Override
	protected void onPause() {
		super.onPause();
		databaseReference.removeEventListener(seenListener);
		status("Desconectado");
		currentUser("none");
	}
}
