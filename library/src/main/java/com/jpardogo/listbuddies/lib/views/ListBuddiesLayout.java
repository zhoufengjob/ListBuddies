package com.jpardogo.listbuddies.lib.views;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v4.widget.AutoScrollHelper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewStub;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.jpardogo.listbuddies.lib.R;
import com.jpardogo.listbuddies.lib.adapters.CircularLoopAdapter;
import com.jpardogo.listbuddies.lib.helpers.ListBuddiesAutoScrollHelper;

/**
 * LinerLayout that contains 2 ListViews. This ListViews auto-scroll while the user is not interacting with them.
 * When the user interact with one of the ListViews a parrallax effect is created during its scroll.
 */
public class ListBuddiesLayout extends LinearLayout implements View.OnTouchListener, ObservableListView.ListViewObserverDelegate {

    private static final String TAG = ListBuddiesLayout.class.getSimpleName();
    private static final long PRESS_DELAY = 100;
    private static final float CANCEL_CLICK_LIMIT = 8;
    private static final int DEFAULT_SPEED = 2;
    private static int ATTR_NOT_SET = -1;

    private OnBuddyItemClickListener mItemBuddyListener;
    private int[] mListViewCoords = new int[2];
    private int mLastViewTouchId;
    private int mDownPosition;
    private int mChildCount;
    private float mDownEventY;
    private ObservableListView mListViewLeft;
    private ObservableListView mListViewRight;
    private boolean mActionDown;
    private boolean isRightListEnabled = false;
    private boolean isLeftListEnabled = false;
    private boolean isUserInteracting = true;
    private View mDownView;
    private Rect mRect = new Rect();
    private ListBuddiesAutoScrollHelper mScrollHelper;
    private int mGap;
    private int mGapColor;
    private int mSpeed;
    private int mSpeedLeft;
    private int mSpeedRight;
    private Drawable mDivider;
    private int mDividerHeight;
    private boolean isAutoScrollLeftListFaster;
    private boolean isScrollLeftListFaster;

    public ListBuddiesLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        setListBuddiesAttributes(context,attrs);
        LayoutInflater.from(context).inflate(R.layout.listbuddies, this, true);
        mListViewLeft = (ObservableListView) findViewById(R.id.list_left);
        mListViewRight = (ObservableListView) findViewById(R.id.list_right);
        mLastViewTouchId = mListViewRight.getId();
        setViewParams();
        setListeners();
        setScrollHelpers();
        startAutoScroll();
    }

    private void setListBuddiesAttributes(Context context,AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ListBuddiesOptions, 0, 0);
        mGap = a.getDimensionPixelSize(R.styleable.ListBuddiesOptions_gap, getResources().getDimensionPixelSize(R.dimen.default_margin_between_lists));
        mSpeed = a.getInteger(R.styleable.ListBuddiesOptions_speed, DEFAULT_SPEED);
        int scrollLeftListFaster = a.getInteger(R.styleable.ListBuddiesOptions_autoScrollFaster,1);
        isAutoScrollLeftListFaster = scrollLeftListFaster==1;
        isScrollLeftListFaster = a.getInteger(R.styleable.ListBuddiesOptions_scrollFaster,scrollLeftListFaster)==1;
        calibrateSpeed();
        mDivider = a.getDrawable(R.styleable.ListBuddiesOptions_listsDivider);
        mDividerHeight = a.getDimensionPixelSize(R.styleable.ListBuddiesOptions_listsDividerHeight, ATTR_NOT_SET);
        mGapColor = a.getColor(R.styleable.ListBuddiesOptions_fillGap,ATTR_NOT_SET);
        a.recycle();
    }

    private void calibrateSpeed() {
        //Parallax doesnt work with speed 1
        if (mSpeed == 1) {
            mSpeed = DEFAULT_SPEED;
        }

        if(isAutoScrollLeftListFaster){
            mSpeedLeft=mSpeed;
            mSpeedRight=mSpeed/2;
        }else{
            mSpeedLeft=mSpeed/2;
            mSpeedRight=mSpeed;
        }
    }

    private void setViewParams() {
        setGap();
        setDivider(mDivider, mDividerHeight);
    }

    private void setGap() {
        if(mGapColor!=ATTR_NOT_SET){
            fillGap();
        }else{
            emptyGap();
        }
    }

    private void emptyGap() {
        LinearLayout.LayoutParams params = (LayoutParams) mListViewLeft.getLayoutParams();
        params.setMargins(0, 0, mGap, 0);
        mListViewLeft.setLayoutParams(params);
    }

    private void fillGap() {
        View gap = ((ViewStub) findViewById(R.id.gap)).inflate();
        LinearLayout.LayoutParams params = (LayoutParams) gap.getLayoutParams();
        params.width = mGap;
        gap.setLayoutParams(params);
        gap.setBackgroundColor(mGapColor);
    }

    private void setHeightDivider(int dividerHeight) {
        if(dividerHeight>ATTR_NOT_SET){
            mListViewLeft.setDividerHeight(dividerHeight);
            mListViewRight.setDividerHeight(dividerHeight);
        }
    }

    private void setDivider(Drawable divider, int dividerHeight) {
        setDivider(divider);
        setHeightDivider(dividerHeight);
    }

    private void setDivider(int divider) {
        if (divider == 0) {
            throw new Resources.NotFoundException("The resource id for the divider cannot be 0");
        }
        Drawable drawable = getResources().getDrawable(divider);
        setDivider(drawable);
    }

    private void setDivider(Drawable divider) {
        if(divider!=null){
            mListViewLeft.setDivider(divider);
            mListViewRight.setDivider(divider);
        }
    }

    private void setListeners() {
        mListViewLeft.setOnTouchListener(this);
        mListViewLeft.setObserver(this);
        setOnListScrollListener(mListViewLeft, true);
        mListViewRight.setOnTouchListener(this);
        mListViewRight.setObserver(this);
        setOnListScrollListener(mListViewRight, false);
    }

    private void setOnListScrollListener(ObservableListView list, final boolean isLeftList) {
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
                switch (state) {
                    case SCROLL_STATE_IDLE:
                        setListState(true, isLeftList);
                        forceScrollIfNeeded(isOtherListEnable(isLeftList));
                        break;

                    case SCROLL_STATE_TOUCH_SCROLL:
                        setListState(false, isLeftList);
                        isUserInteracting = true;
                        break;
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                                 int totalItemCount) {
            }
        });
    }

    private boolean isOtherListEnable(boolean isLeftList) {
        boolean result;
        if (isLeftList) {
            result = isRightListEnabled;
        } else {
            result = isLeftListEnabled;
        }
        return result;
    }


    private void setScrollHelpers() {
        mScrollHelper = new ListBuddiesAutoScrollHelper(mListViewLeft) {
            @Override
            public void scrollTargetBy(int deltaX, int deltaY) {
                mListViewLeft.smoothScrollBy(mSpeedLeft, 0);
                mListViewRight.smoothScrollBy(mSpeedRight, 0);
            }

            @Override
            public boolean canTargetScrollHorizontally(int i) {
                return false;
            }

            @Override
            public boolean canTargetScrollVertically(int i) {
                return true;
            }
        };

        mScrollHelper.setEnabled(isEnable());
        mScrollHelper.setEdgeType(AutoScrollHelper.EDGE_TYPE_OUTSIDE);
    }

    private boolean isEnable() {
        return mSpeed != 0;
    }

    private void setListState(boolean isEnabled, boolean isLeftList) {
        if (isLeftList) {
            isLeftListEnabled = isEnabled;
        } else {
            isRightListEnabled = isEnabled;
        }
    }

    private void startAutoScroll() {
        mListViewLeft.post(new Runnable() {
            @Override
            public void run() {
                forceScroll();
            }
        });
    }

    private void forceScrollIfNeeded(boolean isListEnabled) {
        if (isUserInteracting && isListEnabled) {
            isUserInteracting = false;
            if (!mActionDown) {
                forceScroll();
            }
        }
    }

    public void setAdapters(CircularLoopAdapter adapter, CircularLoopAdapter adapter2) {
        mListViewLeft.setAdapter(adapter);
        mListViewRight.setAdapter(adapter2);
        mListViewLeft.setSelection(Integer.MAX_VALUE / 2);
        mListViewRight.setSelection(Integer.MAX_VALUE / 2);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        ListView list = (ListView) v;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                actionDown(list, event);
                break;
            case MotionEvent.ACTION_UP:
                actionUp(list);
                break;
            case MotionEvent.ACTION_MOVE:
                actionMove(event);
                break;
        }
        return mScrollHelper.onTouch(v, event);
    }

    private void actionDown(ListView list, MotionEvent event) {
        mActionDown = true;
        toogleListView(list);
        mLastViewTouchId = list.getId();
        startClickSelection(event, list, event.getY());
    }

    private void actionUp(ListView list) {
        mActionDown = false;
        performClick(list);
    }

    private void actionMove(MotionEvent event) {
        cancelClick(event.getY());
    }

    private void cancelClick(float eventY) {
        if (mDownView != null && (Math.abs(mDownEventY - eventY) > CANCEL_CLICK_LIMIT)) {
            mDownView.setPressed(false);
            mDownView = null;
        }
    }

    private void performClick(ListView list) {
        if (mDownView != null && (isUserInteracting || mSpeed == 0)) {
            mDownView.setPressed(false);
            if (mItemBuddyListener != null) {
                int buddy = list.getId() == mListViewLeft.getId() ? 0 : 1;
                mItemBuddyListener.onBuddyItemClicked(list, mDownView, buddy, mDownPosition, mDownView.getId());
            }
        }
    }

    private void startClickSelection(MotionEvent event, ListView list, float eventY) {
        if (!isUserInteracting || mSpeed == 0) {
            findViewClicked(event, eventY, list);

            setSelectorPressed(list);
        }
    }

    private void findViewClicked(MotionEvent event, float eventY, ListView list) {
        mChildCount = list.getChildCount();
        mListViewCoords = new int[2];
        list.getLocationOnScreen(mListViewCoords);
        int x = (int) event.getRawX() - mListViewCoords[0];
        int y = (int) event.getRawY() - mListViewCoords[1];
        View child;
        for (int i = 0; i < mChildCount; i++) {
            child = list.getChildAt(i);
            child.getHitRect(mRect);
            if (mRect.contains(x, y)) {
                mDownView = child;
                mDownEventY = eventY;
                break;
            }
        }
    }

    private void setSelectorPressed(ListView list) {
        if (mDownView != null) {
            mDownPosition = list.getPositionForView(mDownView);
            mDownView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isUserInteracting) {
                        if (mDownView != null) {
                            mDownView.setPressed(true);
                        }
                    }
                }
            }, PRESS_DELAY);
        }
    }

    /**
     * Receives the distance scroll on listView.
     */
    @Override
    public void onListScroll(View view, float deltaY) {
        int speed;
        if (view.getId() == mListViewLeft.getId() && !isLeftListEnabled) {
            speed = getSpeed(false,deltaY);

            mListViewRight.smoothScrollBy(speed, 0);
        } else if (view.getId() == mListViewRight.getId() && !isRightListEnabled) {
            speed = getSpeed(true,deltaY);
            mListViewLeft.smoothScrollBy(speed, 0);
        }
    }

    private int getSpeed(boolean isCalculationForLeft, float deltaY) {
        int speed;

        if(isScrollLeftListFaster && isCalculationForLeft || !isScrollLeftListFaster && !isCalculationForLeft){
            speed= (int) -deltaY * 2;
        }else{
            speed=(int) -deltaY / 2;
        }
        return speed;
    }
    /**
     * Each time we touch the opposite ListView than the last one we have selected
     * we need to activate it as the enable one
     */
    private void toogleListView(View v) {
        if (mLastViewTouchId != v.getId()) {
            if (mLastViewTouchId == mListViewLeft.getId()) {
                isLeftListEnabled = true;
                isRightListEnabled = false;
            } else {
                isLeftListEnabled = false;
                isRightListEnabled = true;
            }
        }
    }

    public void setOnItemClickListener(OnBuddyItemClickListener listener) {
        mItemBuddyListener = listener;
    }

    public interface OnBuddyItemClickListener {
        //Buddy corresponde with the list (0-left, 1-right)
        void onBuddyItemClicked(AdapterView<?> parent, View view, int buddy, int position, long id);
    }

    private void forceScroll() {
        MotionEvent event = MotionEvent.obtain(System.currentTimeMillis(), System.currentTimeMillis(), MotionEvent.ACTION_MOVE, 570, -1, 0);
        mScrollHelper.onTouch(mListViewLeft, event);
    }
}
