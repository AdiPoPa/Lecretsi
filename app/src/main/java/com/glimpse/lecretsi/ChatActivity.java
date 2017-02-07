package com.glimpse.lecretsi;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class ChatActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {

    static TextView lastMessageSelected = null;

    public static class MessageViewHolder extends RecyclerView.ViewHolder{
        TextView messageTextView;
        TextView messageDateTime;
        LinearLayout messageLayout;
        LinearLayout messagePosition;

        public MessageViewHolder(View v) {
            super(v);
            messageTextView = (TextView) itemView.findViewById(R.id.userMessage);
            messageDateTime = (TextView) itemView.findViewById(R.id.messageDateTime);
            messageLayout = (LinearLayout) itemView.findViewById(R.id.messageLayout);
            messagePosition = (LinearLayout) itemView.findViewById(R.id.messagePosition);

            messageDateTime.setVisibility(View.GONE);
            messageTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(lastMessageSelected != null)
                        lastMessageSelected.setVisibility(View.GONE);
                    if(messageDateTime.getVisibility() != View.VISIBLE){
                        messageDateTime.setVisibility(View.VISIBLE);
                    } else {
                        messageDateTime.setVisibility(View.GONE);
                    }
                    lastMessageSelected = messageDateTime;
                }
            });
        }
    }

    // This is the activity that's gonna enlist all the different conversations

    ImageButton mSendButton, expandButton;
    RelativeLayout chatLayout;
    private BottomSheetBehavior mBottomSheetBehavior;

    private SharedPreferences mSharedPreferences;
    private GoogleApiClient mGoogleApiClient;

    private static final String TAG = "ChatActivity";
    private static final String TAG_NICKNAME = "nickname";
    private static final String TAG_EMAIL = "email";
    private static final String TAG_LAST_MESSAGE = "lastMessage";
    private static final String TAG_DATE = "date";
    public static final String ANONYMOUS = "anonymous";
    public static final String MESSAGES_CHILD = "-K2ib4H77rj0LYewF7dP";

    private String mUsername;
    private String mEmail;

    private RecyclerView mMessageRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private EditText mMessageEditText;

    // Firebase instance variables
    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;

    private DatabaseReference mFirebaseDatabaseReference;
    private FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder>
            mFirebaseAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        mUsername = ANONYMOUS;

        // Initialize Firebase Auth
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        if (mFirebaseUser == null) {
            // Not signed in, launch the Sign In activity
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        } else {
            mUsername = mFirebaseUser.getDisplayName();
            mEmail = mFirebaseUser.getEmail();
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .build();

        mMessageRecyclerView = (RecyclerView) findViewById(R.id.messageRecyclerView);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setStackFromEnd(true);
        mMessageRecyclerView.setLayoutManager(mLinearLayoutManager);

        // New child entries
        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
        mFirebaseAdapter = new FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder>(
                ChatMessage.class,
                R.layout.user_message,
                MessageViewHolder.class,
                mFirebaseDatabaseReference.child("conversations/" + MESSAGES_CHILD)) {

            @Override
            protected void populateViewHolder(MessageViewHolder viewHolder, ChatMessage chatMessage, int position) {
                if(chatMessage.getEmail().equals(mEmail)) {
                    viewHolder.messageLayout.setGravity(Gravity.RIGHT);
                    viewHolder.messagePosition.setGravity(Gravity.RIGHT);
                    viewHolder.messageTextView.setBackgroundResource(R.drawable.user_text_box);
                    viewHolder.messageTextView.setText(chatMessage.getText());
                    viewHolder.messageDateTime.setText(chatMessage.getDateTime());
                } else {
                    viewHolder.messageLayout.setGravity(Gravity.LEFT);
                    viewHolder.messagePosition.setGravity(Gravity.LEFT);
                    viewHolder.messageTextView.setBackgroundResource(R.drawable.friend_text_box);
                    viewHolder.messageTextView.setText(chatMessage.getText());
                    viewHolder.messageDateTime.setText(chatMessage.getDateTime());
                }
                /*
                if (chatMessage.getPhotoUrl() == null) {
                    viewHolder.messengerImageView
                            .setImageDrawable(ContextCompat
                                    .getDrawable(MainActivity.this,
                                            R.drawable.ic_account_circle_black_36dp));
                } else {
                    Glide.with(MainActivity.this)
                            .load(friendlyMessage.getPhotoUrl())
                            .into(viewHolder.messengerImageView);
                }*/
            }
        };

        mFirebaseAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int friendlyMessageCount = mFirebaseAdapter.getItemCount();
                int lastVisiblePosition =
                        mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
                // If the recycler view is initially being loaded or the
                // user is at the bottom of the list, scroll to the bottom
                // of the list to show the newly added message.
                if (lastVisiblePosition == -1 ||
                        (positionStart >= (friendlyMessageCount - 1) &&
                                lastVisiblePosition == (positionStart - 1))) {
                    mMessageRecyclerView.scrollToPosition(positionStart);
                }
            }
        });

        mFirebaseAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int friendlyMessageCount = mFirebaseAdapter.getItemCount();
                int lastVisiblePosition =
                        mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
                // If the recycler view is initially being loaded or the
                // user is at the bottom of the list, scroll to the bottom
                // of the list to show the newly added message.
                if (lastVisiblePosition == -1 ||
                        (positionStart >= (friendlyMessageCount - 1) &&
                                lastVisiblePosition == (positionStart - 1))) {
                    mMessageRecyclerView.scrollToPosition(positionStart);
                }
            }
        });

        mMessageRecyclerView.setLayoutManager(mLinearLayoutManager);
        mMessageRecyclerView.setAdapter(mFirebaseAdapter);

        chatLayout = (RelativeLayout) findViewById(R.id.chatLayout);
        //messagesLayout = (LinearLayout)findViewById(R.id.messagesLayout);

        mMessageEditText = (EditText) findViewById(R.id.messageText);
        mSendButton = (ImageButton) findViewById(R.id.sendButton);
        expandButton = (ImageButton) findViewById(R.id.expandButton);

        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = mMessageEditText.getText().toString();
                if(text.isEmpty()){
                    expandButton.setVisibility(View.VISIBLE);
                    mSendButton.setVisibility(View.GONE);
                } else {
                    mSendButton.setVisibility(View.VISIBLE);
                    expandButton.setVisibility(View.GONE);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        View bottomSheet = findViewById( R.id.bottom_sheet );
        mBottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        mBottomSheetBehavior.setPeekHeight(0);

        mBottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    mBottomSheetBehavior.setPeekHeight(0);
                }
            }

            @Override
            public void onSlide(View bottomSheet, float slideOffset) {
            }
        });

        Typeface custom_font = Typeface.createFromAsset(getAssets(),  "fonts/assistantfont.ttf");

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                onAssistantMessage("Hello, " + mUsername);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onAssistantMessage("I am your personal Largonji Assistant");
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                onAssistantMessage("What would you want me to translate?");
                            }
                        }, 1000);
                    }
                }, 1000);
            }
        }, 1000);

    }

    public void onSend(View view){
        if(!mMessageEditText.getText().toString().isEmpty()) {
            onUserMessage();
            final String text = Largonji.algorithmToLargonji(mMessageEditText.getText().toString());
            mMessageEditText.setText("");
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    randomStartPhrase();
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onAssistantMessage(text);
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    randomEndPhrase();
                                }
                            }, 500);
                        }
                    }, 500);
                }
            }, 500);
        }
    }

    public void onExpand(View view){
        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    DateFormat df = new SimpleDateFormat("d EEE • HH:mm");
    String date = df.format(Calendar.getInstance().getTime());

    public void onUserMessage(){
        ChatMessage chatMessage = new
                ChatMessage(mUsername, mEmail, mMessageEditText.getText().toString(), date);
        mFirebaseDatabaseReference.child("conversations/" + MESSAGES_CHILD)
                .push().setValue(chatMessage);
    }

    public void onAssistantMessage(String message){
        if(message != null) {
            ChatMessage chatMessage = new
                    ChatMessage("Largonji Assistant", "largonji@assistant.com", message, date);
            mFirebaseDatabaseReference.child("conversations/" + MESSAGES_CHILD)
                    .push().setValue(chatMessage);
        }
    }

    public void randomStartPhrase(){
        int randomNum = 1 + (int)(Math.random() * 3);
        String startPhrases[] = new String[4];
        startPhrases[1] = "Here's your phrase in Largonji";
        onAssistantMessage(startPhrases[randomNum]);
    }

    public void randomEndPhrase(){
        int randomNum = 1 + (int)(Math.random() * 3);
        String endPhrases[] = new String[4];
        endPhrases[1] = "Anything else?";
        endPhrases[2] = "What else?";
        onAssistantMessage(endPhrases[randomNum]);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

}