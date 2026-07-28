package Redknight.thread.BLE.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import Redknight.thread.BLE.R;
import Redknight.thread.BLE.items.Tokens;
import kotlinx.coroutines.channels.ActorKt;

public class TokenListAdapter extends RecyclerView.Adapter<TokenViewHolder> {
        List<Tokens> tokenList;
        private final OnTokenActionListener actionListener;

    public TokenListAdapter(List<Tokens> tokenList,OnTokenActionListener actionListener) {
        this.tokenList = tokenList;
        this.actionListener= actionListener;
    }

    public interface OnTokenActionListener {
        void onSendToken(Tokens item);
    }


    @NonNull
    @Override
    public TokenViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_token, parent, false);
        return new TokenViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TokenViewHolder holder, int position) {

        Tokens item = tokenList.get(position);

        holder.itemStatus.setText(String.format("Meter Number: %s", item.getMeterNumber()));
        holder.itemToken.setText(item.getDisplayToken());

        if(item.isUsed()){
            holder.btnSend.setEnabled(false);
            holder.btnSend.setText("USED");
            holder.btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#BDC3C7")));
            holder.itemView.setAlpha(0.52f);

        }
        else{
            holder.btnSend.setEnabled(true);
            holder.btnSend.setText("SEND");
            holder.btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2962FF")));
            holder.itemView.setAlpha(1.0f);

            holder.btnSend.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onSendToken(item);
                }
            });
        }


    }

    @Override
    public int getItemCount() {
       return tokenList.size();
    }
}

class TokenViewHolder extends  RecyclerView.ViewHolder {

    TextView itemStatus, itemToken, itemDate;
    Button btnSend;

    public TokenViewHolder(@NonNull View itemView) {
        super(itemView);

        itemStatus = itemView.findViewById(R.id.txt_item_status);
        itemToken = itemView.findViewById(R.id.txt_item_token);
        itemDate = itemView.findViewById(R.id.txt_item_date);
        btnSend = itemView.findViewById(R.id.btn_item_send);
    }
}
