;(function () {
    const VISIT_UNIQUE_VISITOR_NAME = 'ujcms-visit-unique-visitor';
    const VISIT_SESSION_ID_NAME = 'ujcms-visit-session-id';
    const VISIT_ENTRY_URL_NAME = 'ujcms-visit-entry-url';
    const VISIT_NEW_VISITOR_NAME = 'ujcms-new-visitor';
    const VISIT_START_NAME = 'ujcms-visit-start';
    const VISIT_DURATION_NAME = 'ujcms-visit-duration';
    const VISIT_OPENS_NAME = 'ujcms-visit-opens';
    const VISIT_HIDDEN_NAME = 'ujcms-visit-hidden';

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

    function getStartTime() {
        let startTime = Cookies.get(VISIT_START_NAME);
        return startTime ? parseInt(startTime) : undefined;
    }

    function setStartTime(startTime) {
        if (startTime != null) {
            Cookies.set(VISIT_START_NAME, String(startTime));
        } else {
            Cookies.remove(VISIT_START_NAME);
        }
    }

    function getHiddenTime() {
        let hiddenTime = Cookies.get(VISIT_HIDDEN_NAME);
        return hiddenTime ? parseInt(hiddenTime) : undefined;
    }

    function setHiddenTime(hiddenTime) {
        if (hiddenTime != null) {
            Cookies.set(VISIT_HIDDEN_NAME, String(hiddenTime));
        } else {
            Cookies.remove(VISIT_HIDDEN_NAME);
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

    function resetCookie() {
        setNewVisitor(false);
        Cookies.remove(VISIT_SESSION_ID_NAME);
        Cookies.remove(VISIT_START_NAME);
        Cookies.remove(VISIT_DURATION_NAME);
    }

    function plusOpens() {
        // 超过2小时没有刷新页面或打开页面，重新计时
        if (getHiddenTime() != null && Date.now() - getHiddenTime() > 2 * 60 * 60 * 1000) {
            resetCookie();
        }
        setHiddenTime(null);
        let visitOpens = Cookies.get(VISIT_OPENS_NAME);
        if (visitOpens == null) {
            visitOpens = '0';
        }
        let opens = parseInt(visitOpens) + 1;
        Cookies.set(VISIT_OPENS_NAME, String(opens));
        if (opens > 0 && getStartTime() == null) {
            setStartTime(Date.now());
        }
        return opens;
    }

    function minusOpens() {
        let visitOpens = Cookies.get(VISIT_OPENS_NAME);
        if (!visitOpens) {
            visitOpens = '0';
        }
        let opens = parseInt(visitOpens) - 1;
        if (opens < 0) {
            opens = 0;
        }
        Cookies.set(VISIT_OPENS_NAME, String(opens));
        const visitStart = getStartTime();
        if (opens <= 0 && visitStart != null) {
            const duration = Math.floor((Date.now() - parseInt(visitStart)) / 1000);
            // 保存浏览时长，清除开始时间
            setDuration(getDuration() + duration);
            setStartTime(null);
            // 设置隐藏时间
            setHiddenTime(Date.now());
        }
        return opens;
    }

    function getCurrentDuration() {
        const visitStart = getStartTime();
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

    function fetchCsrf(callback) {
        axios.get(visitApi + '/env/csrf').then(function (response) {
            const data = response.data;
            if (data && data.parameterName && data.token) {
                callback(data.parameterName, data.token);
            } else {
                throw new Error('CSRF no response data.');
            }
        });
    }

    // 发送请求
    function sendRequest() {
        fetchCsrf(function (csrfName, csrfValue) {
            // const xhr = new XMLHttpRequest();
            const formData = new FormData();
            formData.append(csrfName, csrfValue);
            axios.post(getVisitUrl(1), formData);
            // xhr.open('POST', getVisitUrl(1), false);
            // xhr.send(formData);
        });
    }

    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') {
            minusOpens();
            fetchCsrf(function (csrfName, csrfValue) {
                const formData = new FormData();
                formData.append(csrfName, csrfValue);
                navigator.sendBeacon(getVisitUrl(0), formData);
            });
        } else if (document.visibilityState === 'visible') {
            plusOpens();
        }
    });
    if (document.visibilityState === 'visible') {
        plusOpens();
    }
    sendRequest();
}());
