package com.eaccid.hocreader.presentation.weditor.adapter;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.eaccid.hocreader.R;
import com.eaccid.hocreader.App;
import com.eaccid.hocreader.provider.semantic.SoundPlayer;
import com.eaccid.hocreader.provider.semantic.TranslationSoundPlayer;
import com.eaccid.hocreader.provider.db.words.WordItemImpl;
import com.eaccid.hocreader.provider.db.words.WordListInteractor;
import com.eaccid.hocreader.provider.db.words.listprovider.ItemDataProvider;
import com.eaccid.hocreader.exceptions.ReaderExceptionHandlerImpl;
import com.eaccid.hocreader.underdevelopment.WordViewElements;
import com.eaccid.hocreader.underdevelopment.WordViewHandler;
import com.h6ah4i.android.widget.advrecyclerview.swipeable.SwipeableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.swipeable.SwipeableItemConstants;
import com.h6ah4i.android.widget.advrecyclerview.swipeable.action.SwipeResultAction;
import com.h6ah4i.android.widget.advrecyclerview.swipeable.action.SwipeResultActionDefault;
import com.h6ah4i.android.widget.advrecyclerview.swipeable.action.SwipeResultActionMoveToSwipedDirection;
import com.h6ah4i.android.widget.advrecyclerview.swipeable.action.SwipeResultActionRemoveItem;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractSwipeableItemViewHolder;
import com.h6ah4i.android.widget.advrecyclerview.utils.RecyclerViewAdapterUtils;
import com.ms.square.android.expandabletextview.ExpandableTextView;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public class SwipeOnLongPressRecyclerViewAdapter
        extends RecyclerView.Adapter<SwipeOnLongPressRecyclerViewAdapter.WordsEditorViewHolder>
        implements SwipeableItemAdapter<SwipeOnLongPressRecyclerViewAdapter.WordsEditorViewHolder> {
    private final String LOG_TAG = "OnLongPressRVAdapter";
    private EventListener mEventListener;
    private final View.OnClickListener mItemViewOnClickListener;
    private final View.OnClickListener mSwipeableViewContainerOnClickListener;
    private SparseBooleanArray mSelectedItemsIds;
    private CompositeSubscription compositeSubscription = new CompositeSubscription();

    @Inject
    WordListInteractor wordListInteractor;

    private interface Swipeable extends SwipeableItemConstants {
    }

    public interface EventListener {
        void onItemRemoved(int position);

        void onItemPinned(int position);

        void onItemViewClicked(View v, boolean pinned);
    }

    public SwipeOnLongPressRecyclerViewAdapter() {
        mItemViewOnClickListener = this::onItemViewClick;
        mSwipeableViewContainerOnClickListener = this::onSwipeableViewContainerClick;
        setHasStableIds(true);// have to implement the getItemId() method
        mSelectedItemsIds = new SparseBooleanArray();
    }

    /**
     * Work with view holder
     */

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        App.get(recyclerView.getContext())
                .getWordListComponent()
                .inject(this);
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        compositeSubscription.unsubscribe();
    }

    @Override
    public WordsEditorViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View v = inflater.inflate(R.layout.editor_word_item_fragment_1, parent, false);
        return new WordsEditorViewHolder(v);
    }

    @Override
    public void onBindViewHolder(WordsEditorViewHolder holder, int position) {
        if (holder.subscription != null && !holder.subscription.isUnsubscribed())
            compositeSubscription.remove(holder.subscription);
        holder.subscription = wordListInteractor
                .getWordItem(position)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(wordItem -> new WordViewHandler().loadDataToViewFromWordItem(holder, wordItem),
                        e -> new ReaderExceptionHandlerImpl().handleError(e)
                );
        compositeSubscription.add(holder.subscription);
        // if the item is 'pinned', click event comes to the itemView
        holder.itemView.setOnClickListener(mItemViewOnClickListener);
        // if the item is 'not pinned', click event comes to the container
        holder.container.setOnClickListener(mSwipeableViewContainerOnClickListener);
        holder.showInPage.setOnClickListener(
                new OnShowWordInBookPageClickListener(getWordListItemProvider(position))
        );
        setViewHoldersContainerBackGround(holder, position);
        holder.setSwipeItemHorizontalSlideAmount(
                getWordListItemProvider(position).isPinned() ? Swipeable.OUTSIDE_OF_THE_WINDOW_LEFT : 0
        );
    }

    private void setViewHoldersContainerBackGround(WordsEditorViewHolder holder, int position) {
        final int swipeState = holder.getSwipeStateFlags();
        int bgResId;
        if (mSelectedItemsIds.get(position)) {
            bgResId = R.drawable.bg_item_selected_state;
        } else if (getWordListItemProvider(position).isLastAdded()) {
            bgResId = R.drawable.bg_item_session_state;
        } else {
            bgResId = R.drawable.bg_item_normal_state;
        }
        if ((swipeState & Swipeable.STATE_FLAG_IS_UPDATED) != 0) {
            if ((swipeState & Swipeable.STATE_FLAG_IS_ACTIVE) != 0) {
                bgResId = R.drawable.bg_item_swiping_active_state;
            } else if ((swipeState & Swipeable.STATE_FLAG_SWIPING) != 0) {
                bgResId = R.drawable.bg_item_swiping_state;
            }
        }
        holder.container.setBackgroundResource(bgResId);
    }

    private WordItemImpl getWordListItemProvider(final int position) {
        return (WordItemImpl) wordListInteractor.getItem(position);
    }

    /**
     * by advanced recycler view library example
     */

    @Override
    public long getItemId(int position) {
        return wordListInteractor.getItem(position).getItemId();
    }

    @Override
    public int getItemCount() {
        return wordListInteractor.getCount();
    }

    @Override
    public int onGetSwipeReactionType(WordsEditorViewHolder holder, int position, int x, int y) {
        return Swipeable.REACTION_CAN_SWIPE_LEFT | Swipeable.REACTION_MASK_START_SWIPE_LEFT |
                Swipeable.REACTION_CAN_SWIPE_RIGHT | Swipeable.REACTION_MASK_START_SWIPE_RIGHT |
                Swipeable.REACTION_START_SWIPE_ON_LONG_PRESS;
    }

    @Override
    public void onSetSwipeBackground(WordsEditorViewHolder holder, int position, int type) {
        int bgRes = 0;
        switch (type) {
            case Swipeable.DRAWABLE_SWIPE_NEUTRAL_BACKGROUND:
                bgRes = R.drawable.bg_swipe_item_neutral;
                break;
            case Swipeable.DRAWABLE_SWIPE_LEFT_BACKGROUND:
                bgRes = R.drawable.bg_swipe_item_left;
                break;
            case Swipeable.DRAWABLE_SWIPE_RIGHT_BACKGROUND:
                bgRes = R.drawable.bg_swipe_item_right;
                break;
        }
        holder.itemView.setBackgroundResource(bgRes);
    }

    @Override
    public SwipeResultAction onSwipeItem(WordsEditorViewHolder holder, final int position, int result) {
        Log.d(LOG_TAG, "onSwipeItem(position = " + position + ", result = " + result + ")");

        switch (result) {
            case Swipeable.RESULT_SWIPED_RIGHT:
                if (wordListInteractor.getItem(position).isPinned()) {
                    return new UnpinResultAction(this, position);
                } else {
                    return new SwipeRightResultAction(this, position);
                }
            case Swipeable.RESULT_SWIPED_LEFT:
                return new SwipeLeftResultAction(this, position);
            case Swipeable.RESULT_CANCELED:
            default:
                if (position != RecyclerView.NO_POSITION) {
                    return new UnpinResultAction(this, position);
                } else {
                    return null;
                }
        }
    }

    public void setEventListener(EventListener eventListener) {
        mEventListener = eventListener;
    }

    private void onItemViewClick(View v) {
        if (mEventListener != null) {
            mEventListener.onItemViewClicked(v, true); //true -> isPinned
        }
    }

    private void onSwipeableViewContainerClick(View v) {
        if (mEventListener != null) {
            mEventListener.onItemViewClicked(RecyclerViewAdapterUtils.getParentViewHolderItemView(v), false);  // false -> isPinned
        }
    }

    private static class SwipeLeftResultAction extends SwipeResultActionMoveToSwipedDirection {
        private SwipeOnLongPressRecyclerViewAdapter mAdapter;
        private final int mPosition;
        private boolean mSetPinned;

        SwipeLeftResultAction(SwipeOnLongPressRecyclerViewAdapter adapter, int position) {
            mAdapter = adapter;
            mPosition = position;
        }

        @Override
        protected void onPerformAction() {
            super.onPerformAction();
            ItemDataProvider item = mAdapter.wordListInteractor.getItem(mPosition);
            if (!item.isPinned()) {
                item.setPinned(true);
                mAdapter.notifyItemChanged(mPosition);
                mSetPinned = true;
            }
        }

        @Override
        protected void onSlideAnimationEnd() {
            super.onSlideAnimationEnd();
            if (mSetPinned && mAdapter.mEventListener != null) {
                mAdapter.mEventListener.onItemPinned(mPosition);
            }
        }

        @Override
        protected void onCleanUp() {
            super.onCleanUp();
            mAdapter = null;
        }
    }

    private static class SwipeRightResultAction extends SwipeResultActionRemoveItem {
        private SwipeOnLongPressRecyclerViewAdapter mAdapter;
        private final int mPosition;

        SwipeRightResultAction(SwipeOnLongPressRecyclerViewAdapter adapter, int position) {
            mAdapter = adapter;
            mPosition = position;
        }

        @Override
        protected void onPerformAction() {
            super.onPerformAction();
            mAdapter.wordListInteractor.removeItem(mPosition);
            mAdapter.notifyItemRemoved(mPosition);
        }

        @Override
        protected void onSlideAnimationEnd() {
            super.onSlideAnimationEnd();
            if (mAdapter.mEventListener != null) {
                mAdapter.mEventListener.onItemRemoved(mPosition);
            }
        }

        @Override
        protected void onCleanUp() {
            super.onCleanUp();
            mAdapter = null;
        }
    }

    private static class UnpinResultAction extends SwipeResultActionDefault {
        private SwipeOnLongPressRecyclerViewAdapter mAdapter;
        private final int mPosition;

        UnpinResultAction(SwipeOnLongPressRecyclerViewAdapter adapter, int position) {
            mAdapter = adapter;
            mPosition = position;
        }

        @Override
        protected void onPerformAction() {
            super.onPerformAction();
            ItemDataProvider item = mAdapter.wordListInteractor.getItem(mPosition);
            if (item.isPinned()) {
                item.setPinned(false);
                mAdapter.notifyItemChanged(mPosition);
            }
        }

        @Override
        protected void onCleanUp() {
            super.onCleanUp();
            mAdapter = null;
        }
    }

    /***
     * Methods required for do selections
     */

    public void toggleSelection(int position) {
        selectView(position, !mSelectedItemsIds.get(position));
    }

    public void removeSelection() {
        mSelectedItemsIds = new SparseBooleanArray();
        notifyDataSetChanged();
    }

    private void selectView(int position, boolean value) {
        if (value)
            mSelectedItemsIds.put(position, value);
        else
            mSelectedItemsIds.delete(position);
        notifyItemChanged(position);
    }

    public int getSelectedCount() {
        return mSelectedItemsIds.size();
    }

    public SparseBooleanArray getSelectedIds() {
        return mSelectedItemsIds;
    }

    static class WordsEditorViewHolder extends AbstractSwipeableItemViewHolder implements WordViewElements {
        @BindView(R.id.container)
        FrameLayout container;
        @BindView(R.id.word)
        TextView word;
        @BindView(R.id.word_transcription)
        TextView transcription;
        @BindView(R.id.translation)
        TextView translation;
        @BindView(R.id.expand_text_view)
        ExpandableTextView context;
        @BindView(R.id.word_image)
        ImageView wordImage;
        @BindView(R.id.show_in_page)
        ImageView showInPage;
        @BindView(R.id.learn_by_heart_false)
        ImageView learnByHeart;
        @BindView(R.id.already_learned)
        ImageView alreadyLearned;
        @BindView(R.id.transcription_speaker)
        ImageView transcriptionSpeaker;
        SoundPlayer<String> soundPlayer;
        Subscription subscription;

        WordsEditorViewHolder(View v) {
            super(v);
            ButterKnife.bind(this, v);
            soundPlayer = new TranslationSoundPlayer();
        }

        @Override
        public View getSwipeableContainerView() {
            return container;
        }

        @Override
        public TextView word() {
            return word;
        }

        @Override
        public TextView transcription() {
            return transcription;
        }

        @Override
        public TextView translation() {
            return translation;
        }

        @Override
        public ImageView wordImage() {
            return wordImage;
        }

        @Override
        public int defaultImageResId() {
            return R.drawable.empty_picture_background;
        }

        @Override
        public ImageView learnByHeart() {
            return learnByHeart;
        }

        @Override
        public ImageView alreadyLearned() {
            return alreadyLearned;
        }

        @Override
        public ImageView transcriptionSpeaker() {
            return transcriptionSpeaker;
        }

        @Override
        public SoundPlayer<String> soundPlayer() {
            return soundPlayer;
        }

        @Override
        public View container() {
            return itemView;
        }

        @Override
        public ExpandableTextView context() {
            return context;
        }
    }

}
