package org.wheel.context;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public interface MultiVersionContextContainer {

    /**
     * 비동기로 {@link ContextLoader#load(String, File[], Map)} 메소드를 호출한다.
     *
     * @return {@link Future} of {@link ContextLoadImage}
     */
    Future<ContextLoadImage> startLoad();

    /**
     * 현재 이 컨테이너에 활성화된 {@link ContextLoadImage}를 반환한다.
     *
     * @return {@link Future} of {@link ContextLoadImage}
     */
    ContextLoadImage current();

    /**
     * 이 컨테이너의 current() image를 가장 최근의 success image로 변경한다.
     * 만약 current() image가 success 상태라면 아무 변경도 일어나지 않으며,
     * current() image가 failure 상태인 경우에만 의미가 있다.
     * 만약 latest success image가 존재하지 않는 경우 IllegalStateException이 발생한다.
     *
     * @return {@link Future} of {@link ContextLoadImage}
     */
    ContextLoadImage switchToLatestSuccess();

    /**
     * index에 해당하는 image를 success/failure 상태 상관 없이 current()로 강제 변경한다.
     *
     * @param index
     *
     * @return {@link Future} of {@link ContextLoadImage}
     */
    ContextLoadImage forceSwitchTo(int index);

    /**
     * index에 해당하는 image를 history에서 삭제한다.
     *
     * @param index
     *
     * @return {@link Future} of {@link ContextLoadImage}
     */
    ContextLoadImage removeFromHistory(int index);

    /**
     * load history의 크기를 반환한다.
     * @return
     */
    int size();

    /**
     * history의 복사본을 반환한다. 복사본이므로 변경사항이 영향을 미치지 않는다.
     * 단, 내부의 ContextLoadImage 객체의 상태 중 applicationContext는 강제로 close()되는 경우가 있다.
     *
     * @return
     */
    List<ContextLoadImage> getHistoryCopy();

    /**
     * 이 컨테이너에 대한 리스너를 등록한다.
     * @param listener
     */
    void addListener(MultiVersionContextContainerListener listener);
}
