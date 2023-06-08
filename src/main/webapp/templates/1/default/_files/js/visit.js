;(function () {
    const VISIT_UNIQUE_VISITOR_NAME = 'ujcms-visit-unique-visitor';
    const VISIT_SESSION_ID_NAME = 'ujcms-visit-session-id';
    const VISIT_ENTRY_URL_NAME = 'ujcms-visit-entry-url';
    const VISIT_NEW_VISITOR_NAME = 'ujcms-new-visitor';
    const VISIT_START_NAME = 'ujcms-visit-start';
    const VISIT_DURATION_NAME = 'ujcms-visit-duration';
    const VISIT_OPENS_NAME = 'ujcms-visit-opens';

    // 获取独立访客标识
    function getEntryUrl() {
        let entryUrl = Cookies.get(VISIT_ENTRY_URL_NAME);
        if (!entryUrl) {
            entryUrl = document.location.href;
            Cookies.set(VISIT_ENTRY_URL_NAME, entryUrl);
        }
        return entryUrl;
    }

    function getNewVisitor() {
        const newVisitor = Cookies.get(VISIT_NEW_VISITOR_NAME);
        return newVisitor === 'true';
    }

    function setNewVisitor(newVisitor) {
        Cookies.set(VISIT_NEW_VISITOR_NAME, String(newVisitor));
    }

    // 获取独立访客标识
    function getUniqueVisitor() {
        let uv = Cookies.get(VISIT_UNIQUE_VISITOR_NAME);
        if (!uv) {
            uv = String(Math.floor(Math.random() * Number.MAX_SAFE_INTEGER));
            Cookies.set(VISIT_UNIQUE_VISITOR_NAME, uv, {expires: 3650});
            setNewVisitor(true);
        }
        return uv;
    }

    // 获取会话标识
    function getSessionId() {
        let si = Cookies.get(VISIT_SESSION_ID_NAME);
        if (!si) {
            si = String(Math.floor(Math.random() * Number.MAX_SAFE_INTEGER));
            Cookies.set(VISIT_SESSION_ID_NAME, si);
        }
        return si;
    }

    function getStart() {
        let start = Cookies.get(VISIT_START_NAME);
        return start ? parseInt(start) : undefined;
    }

    function setStart(start) {
        if (start != null) {
            Cookies.set(VISIT_START_NAME, String(start));
        } else {
            Cookies.remove(VISIT_START_NAME);
        }
    }

    // 获取访问时长
    function getDuration() {
        let duration = Cookies.get(VISIT_DURATION_NAME);
        return duration ? parseInt(duration) : 0;
    }

    function setDuration(duration) {
        Cookies.set(VISIT_DURATION_NAME, String(duration));
    }

    function plusOpens() {
        let visitOpens = Cookies.get(VISIT_OPENS_NAME);
        if (visitOpens == null) {
            visitOpens = '0';
        }
        let opens = parseInt(visitOpens) + 1;
        Cookies.set(VISIT_OPENS_NAME, String(opens));
        if (opens > 0 && getStart() == null) {
            setStart(Date.now());
        }
        return opens;
    }

    function minusOpens() {
        let visitOpens = Cookies.get(VISIT_OPENS_NAME);
        if (!visitOpens) {
            visitOpens = '0';
        }
        let opens = parseInt(visitOpens) - 1;
        Cookies.set(VISIT_OPENS_NAME, String(opens));
        const visitStart = getStart();
        if (opens <= 0 && visitStart != null) {
            const duration = Math.floor((Date.now() - parseInt(visitStart)) / 1000);
            setDuration(getDuration() + duration);
            // 清除开始时间
            setStart(null);
        }
        return opens;
    }

    function getCurrentDuration() {
        const visitStart = getStart();
        if (visitStart != null) {
            const duration = Math.floor((Date.now() - parseInt(visitStart)) / 1000);
            return getDuration() + duration;
        }
        return getDuration();
    }

    function getVisitUrl(count) {
        const newVisitor = count >= 1 ? String(getNewVisitor()) : 'false';
        return visitApi + '/visit/' + visitSiteId + '?url=' + encodeURIComponent(document.location.href) +
            decodeURI('&entryUrl=') + encodeURIComponent(getEntryUrl()) +
            decodeURI('&referrer=') + encodeURIComponent(document.referrer) +
            decodeURI('&si=') + getSessionId() +
            decodeURI('&uv=') + getUniqueVisitor() +
            decodeURI('&newVisitor=') + newVisitor +
            decodeURI('&duration=') + getCurrentDuration() +
            decodeURI('&count=') + count;
    }

    // 发送请求
    function sendRequest() {
        const xhr = new XMLHttpRequest();
        const formData = new FormData();
        formData.append(csrfName, csrfValue);
        xhr.open('POST', getVisitUrl(1), false);
        xhr.send(formData);
    }

    plusOpens();
    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') {
            minusOpens();
            const formData = new FormData();
            formData.append(csrfName, csrfValue);
            navigator.sendBeacon(getVisitUrl(0), formData);
        } else if (document.visibilityState === 'visible') {
            plusOpens();
        }
    });
    sendRequest();
}());
