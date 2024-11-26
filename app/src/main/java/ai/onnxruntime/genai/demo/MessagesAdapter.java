import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessageViewHolder> {

    private List<Message> messagesList;

    public MessagesAdapter(List<Message> messagesList) {
        this.messagesList = messagesList;
    }

    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == MessageType.SENT.ordinal()) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
        }
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MessageViewHolder holder, int position) {
        Message message = messagesList.get(position);
        holder.messageTextView.setText(message.getContent());
    }

    @Override
    public int getItemViewType(int position) {
        return messagesList.get(position).getType().ordinal();
    }

    @Override
    public int getItemCount() {
        return messagesList.size();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        public TextView messageTextView;

        public MessageViewHolder(View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.message_text);
        }
    }
}
