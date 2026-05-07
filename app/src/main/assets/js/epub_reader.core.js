// ============================================
// EPUB Reader - 核心模块
// ============================================

// ============================================
// 全局状态管理
// ============================================
const AppState = {
    book: null,
    rendition: null,
    isLoaded: false,
    isGeneratedLocations: false,
    isSearching: false,
    handleSearchCompleted: null,
    tableOfContents: [],
    
    // 按钮拖动状态
    dragState: {
        isDragging: false,
        hasMoved: false,
        startX: 0,
        startY: 0,
        initialX: 0,
        initialY: 0,
        currentX: 20,
        currentY: 20,
        isCollapsed: false,
        isTocButtonVisible: true
    },

    reset() {
        this.isLoaded = false;
        this.isGeneratedLocations = false;
        this.isSearching = false;
        this.handleSearchCompleted = null;
    }
};

// ============================================
// Android 接口代理
// ============================================
const AndroidBridge = {
    onPageChanged(jsonLocation) {
        if (window.Android && window.Android.onPageChanged) {
            window.Android.onPageChanged(jsonLocation);
        }
    },

    onLoadComplete() {
        if (window.Android && window.Android.onLoadComplete) {
            window.Android.onLoadComplete();
        }
    },

    onError(message) {
        if (window.Android && window.Android.onError) {
            window.Android.onError(message);
        }
    },

    onHtmlReady() {
        if (window.Android && window.Android.onHtmlReady) {
            window.Android.onHtmlReady();
        }
    },

    onPageActionComplete(action) {
        if (window.Android && window.Android.onPageActionComplete) {
            window.Android.onPageActionComplete(action);
        }
    },

    onDoubleClick() {
        if (window.Android && window.Android.onDoubleClick) {
            window.Android.onDoubleClick();
        }
    },

    onLocationRetrieved(jsonLocation) {
        if (window.Android && window.Android.onLocationRetrieved) {
            window.Android.onLocationRetrieved(jsonLocation);
        }
    },

    onPageTextRetrieved(text) {
        if (window.Android && window.Android.onPageTextRetrieved) {
            window.Android.onPageTextRetrieved(text);
        }
    },

    onSearchingResult(result) {
        if (window.Android && window.Android.onSearchingResult) {
            window.Android.onSearchingResult(result);
        }
    },

    onSearchCompleted() {
        if (window.Android && window.Android.onSearchCompleted) {
            window.Android.onSearchCompleted();
        }
    }
};

// ============================================
// 资源清理模块
// ============================================
const ResourceManager = {
    clearRendition() {
        if (AppState.rendition) {
            console.log('Destroying old rendition...');
            AppState.rendition.destroy();
            AppState.rendition = null;
        }
    },

    clearBook() {
        if (AppState.book) {
            console.log('Destroying old book...');
            AppState.book.destroy();
            AppState.book = null;
        }
    },

    cleanUp() {
        this.clearRendition();
        this.clearBook();
        console.log('Resources cleaned up');
    }
};

// ============================================
// UI 管理模块
// ============================================
const UIManager = {
    init() {
        this.initDraggableButton();
    },

    showLoading() {
        const loadingEl = document.getElementById('loading');
        loadingEl.style.display = 'block';
        loadingEl.style.visibility = 'visible';
        loadingEl.style.pointerEvents = 'auto';
    },

    hideLoading() {
        const loadingEl = document.getElementById('loading');
        loadingEl.style.display = 'none';
        loadingEl.style.visibility = 'hidden';
        loadingEl.style.pointerEvents = 'none';
    },

    showError(message) {
        this.hideLoading();
        document.getElementById('error').style.display = 'block';
        document.getElementById('error-message').textContent = message;
        AndroidBridge.onError(message);
    },

    showTocButton() {
        document.getElementById('toc-btn').style.display = 'block';
    },

    openNavPanel() {
        const navPanel = document.getElementById('nav-panel');
        const navContent = document.getElementById('nav-content');
        const btn = document.getElementById('toc-btn');

        const btnRect = btn.getBoundingClientRect();
        const screenWidth = window.innerWidth;
        const btnCenterX = btnRect.left + btnRect.width / 2;

        if (btnCenterX < screenWidth / 2) {
            navContent.style.right = '0';
            navContent.style.left = 'auto';
            navContent.style.boxShadow = 'var(--shadow-panel)';
            navContent.style.transform = 'translateX(100%)';
        } else {
            navContent.style.left = '0';
            navContent.style.right = 'auto';
            navContent.style.boxShadow = 'var(--shadow-panel)';
            navContent.style.transform = 'translateX(-100%)';
        }

        navPanel.style.display = 'block';
        document.body.style.overflow = 'hidden';

        setTimeout(() => {
            navPanel.style.opacity = '1';
            navContent.style.transform = 'translateX(0)';
        }, 10);
    },

    closeNavPanel() {
        const navPanel = document.getElementById('nav-panel');
        const navContent = document.getElementById('nav-content');

        navPanel.style.opacity = '0';

        if (navContent.style.left === '0px') {
            navContent.style.transform = 'translateX(-100%)';
        } else {
            navContent.style.transform = 'translateX(100%)';
        }

        setTimeout(() => {
            navPanel.style.display = 'none';
            document.body.style.overflow = '';
        }, 300);
    },

    toggleNavPanel() {
        const navPanel = document.getElementById('nav-panel');
        if (navPanel.style.display === 'block') {
            this.closeNavPanel();
        } else {
            this.openNavPanel();
        }
    },

    generateTOC(toc) {
        const container = document.getElementById('toc-container');
        if (!container || !toc || toc.length === 0) {
            container.innerHTML = '<p style="color: var(--color-text-muted); text-align: center; padding: 20px;">暂无目录</p>';
            return;
        }
        container.innerHTML = this.generateTocHtml(toc, 0);
    },

    generateTocHtml(toc, level = 0) {
        let html = '<ul style="list-style: none; padding: 0; margin: 0;">';

        toc.forEach((chapter) => {
            const indent = level * 20;
            const fontSize = Math.max(12, 16 - level * 2);
            const href = chapter.href || '';
            const label = chapter.label || '未命名章节';

            html += `
                <li style="margin: 5px 0;">
                    <a href="#"
                       data-href="${href}"
                       onclick="ChapterManager.jumpToChapter('${href}'); return false;"
                       style="display: block; padding: 12px 18px; padding-left: ${12 + indent}px;
                              text-decoration: none; color: var(--color-text-primary); font-size: ${fontSize}px;
                              border-radius: 5px; transition: background var(--transition-fast);"
                    >
                       ${label}
                    </a>
            `;

            if (chapter.subitems && chapter.subitems.length > 0) {
                html += this.generateTocHtml(chapter.subitems, level + 1);
            }

            html += '</li>';
        });

        html += '</ul>';
        return html;
    },

    highlightCurrentChapter(currentHref) {
        const links = document.querySelectorAll('#toc-container a');
        links.forEach(link => {
            const linkHref = link.getAttribute('data-href');
            const isMatch = linkHref === currentHref ||
                           linkHref.startsWith(currentHref) ||
                           currentHref.startsWith(linkHref.split('#')[0]);

            if (isMatch) {
                link.classList.add('active');
                link.scrollIntoView({ behavior: 'smooth', block: 'center' });
            } else {
                link.classList.remove('active');
            }
        });
    },

    toggleTocButton() {
        AppState.dragState.isTocButtonVisible = !AppState.dragState.isTocButtonVisible;
        const btn = document.getElementById('toc-toggle-btn');
        const tocBtn = document.getElementById('toc-btn');

        this.closeNavPanel();

        if (AppState.dragState.isTocButtonVisible) {
            btn.textContent = '隐藏悬浮按钮';
            tocBtn.style.display = 'block';
        } else {
            btn.textContent = '显示悬浮按钮';
            tocBtn.style.display = 'none';
        }
    },

    initDraggableButton() {
        const btn = document.getElementById('toc-btn');
        if (!btn) return;

        btn.addEventListener('touchstart', this.handleDragStart.bind(this), { passive: false });
        btn.addEventListener('touchmove', this.handleDragMove.bind(this), { passive: false });
        btn.addEventListener('touchend', this.handleDragEnd.bind(this));

        btn.addEventListener('mousedown', this.handleDragStart.bind(this));
        document.addEventListener('mousemove', this.handleDragMove.bind(this));
        document.addEventListener('mouseup', this.handleDragEnd.bind(this));
    },

    handleDragStart(e) {
        const btn = document.getElementById('toc-btn');
        if (!btn || btn.style.display === 'none') return;

        if (AppState.dragState.isCollapsed) {
            this.expandButton(btn);
            setTimeout(() => {
                this.startDragLogic(e, btn);
            }, 300);
            return;
        }
        this.startDragLogic(e, btn);
    },

    startDragLogic(e, btn) {
        AppState.dragState.isDragging = true;
        AppState.dragState.hasMoved = false;

        if (e.type === 'touchstart') {
            AppState.dragState.startX = e.touches[0].clientX;
            AppState.dragState.startY = e.touches[0].clientY;
        } else {
            AppState.dragState.startX = e.clientX;
            AppState.dragState.startY = e.clientY;
            e.preventDefault();
        }

        const rect = btn.getBoundingClientRect();
        AppState.dragState.initialX = rect.left;
        AppState.dragState.initialY = rect.top;

        btn.style.transition = 'none';
    },

    handleDragMove(e) {
        if (!AppState.dragState.isDragging) return;

        const btn = document.getElementById('toc-btn');
        if (!btn) return;

        let clientX, clientY;
        if (e.type === 'touchmove') {
            clientX = e.touches[0].clientX;
            clientY = e.touches[0].clientY;
        } else {
            clientX = e.clientX;
            clientY = e.clientY;
        }

        const deltaX = clientX - AppState.dragState.startX;
        const deltaY = clientY - AppState.dragState.startY;

        if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
            AppState.dragState.hasMoved = true;
        }

        let newX = AppState.dragState.initialX + deltaX;
        let newY = AppState.dragState.initialY + deltaY;

        const maxX = window.innerWidth - btn.offsetWidth;
        const maxY = window.innerHeight - btn.offsetHeight;

        newX = Math.max(0, Math.min(newX, maxX));
        newY = Math.max(0, Math.min(newY, maxY));

        btn.style.left = newX + 'px';
        btn.style.top = newY + 'px';
        btn.style.right = 'auto';

        AppState.dragState.currentX = newX;
        AppState.dragState.currentY = newY;

        if (e.type === 'touchmove') {
            e.preventDefault();
        }
    },

    handleDragEnd(e) {
        if (!AppState.dragState.isDragging) return;

        const btn = document.getElementById('toc-btn');
        if (!btn) {
            AppState.dragState.isDragging = false;
            return;
        }

        AppState.dragState.isDragging = false;

        if (AppState.dragState.hasMoved) {
            this.checkAndSnapToEdge(btn);
        }
    },

    checkAndSnapToEdge(btn) {
        const screenWidth = window.innerWidth;
        const rect = btn.getBoundingClientRect();
        const currentLeft = rect.left;
        const currentRight = screenWidth - rect.right;

        const snapThreshold = 30;

        if (currentLeft <= snapThreshold) {
            this.collapseToEdge(btn, 'left');
        } else if (currentRight <= snapThreshold) {
            this.collapseToEdge(btn, 'right');
        }
    },

    collapseToEdge(btn, side) {
        AppState.dragState.isCollapsed = true;
        const screenWidth = window.innerWidth;
        const btnHeight = btn.offsetHeight;

        let targetY = parseFloat(btn.style.top) || 20;
        const maxY = window.innerHeight - btnHeight / 4 - 10;
        targetY = Math.max(10, Math.min(targetY, maxY));

        const isLeft = side === 'left';

        btn.style.transition = 'all var(--transition-normal) ease';
        btn.style.left = isLeft ? '0px' : (screenWidth - 12.5) + 'px';
        btn.style.top = targetY + 'px';
        btn.style.width = '16px';
        btn.style.height = btnHeight + 'px';
        btn.style.borderRadius = isLeft ? '0 12.5px 12.5px 0' : '12.5px 0 0 12.5px';
        btn.style.paddingLeft = isLeft ? '2px' : '0';
        btn.style.paddingRight = isLeft ? '0' : '2px';
        btn.style.fontSize = '16px';

        AppState.dragState.currentX = isLeft ? 0 : screenWidth - 12.5;
        AppState.dragState.currentY = targetY;

        setTimeout(() => {
            btn.style.transition = '';
        }, 300);
    },

    expandButton(btn) {
        if (!AppState.dragState.isCollapsed) return;

        AppState.dragState.isCollapsed = false;

        btn.style.transition = 'all var(--transition-normal) ease';
        btn.style.width = '50px';
        btn.style.height = '50px';
        btn.style.borderRadius = '50%';
        btn.style.paddingLeft = '0';
        btn.style.paddingRight = '0';

        const screenWidth = window.innerWidth;
        const rect = btn.getBoundingClientRect();
        const centerX = rect.left + 25;

        if (centerX < screenWidth / 2) {
            btn.style.left = '10px';
        } else {
            btn.style.left = (screenWidth - 60) + 'px';
        }

        setTimeout(() => {
            btn.style.transition = '';
        }, 300);
    },

    handleTocBtnClick() {
        const btn = document.getElementById('toc-btn');
        if (!btn) return;

        if (AppState.dragState.isCollapsed) {
            this.expandButton(btn);
            return;
        }

        if (AppState.dragState.hasMoved) {
            AppState.dragState.hasMoved = false;
            return;
        }

        this.openNavPanel();
    }
};

// ============================================
// 阅读器核心模块
// ============================================
const ReaderCore = {
    init(epubUrl, startCfi) {
        console.log('=== EPUB Reader Init Start ===');
        console.log('EPUB URL:', epubUrl);
        console.log('Start CFI:', startCfi || '(none, will show first page)');

        try {
            this.validateLibraries();
            
            ResourceManager.clearRendition();
            ResourceManager.clearBook();
            AppState.reset();

            UIManager.showLoading();

            this.createBook(epubUrl);
            this.createRendition();
            this.setupEventListeners();
            this.loadNavigation();
            this.generateLocationsAsync();
            this.displayBook(startCfi);

        } catch (error) {
            console.error('Init error:', error);
            UIManager.showError("初始化失败: " + error.message);
        }
    },

    validateLibraries() {
        if (typeof ePub === 'undefined') {
            throw new Error('ePub library not loaded. Check js/epub.min.js file.');
        }
    },

    createBook(epubUrl) {
        console.log('Creating ePub instance...');
        AppState.book = ePub(epubUrl);
        console.log('ePub instance created:', AppState.book);
    },

    createRendition() {
        console.log('Creating renderer...');
        AppState.rendition = AppState.book.renderTo("viewer", {
            width: "100%",
            height: "100%",
            flow: "paginated",
            manager: "continuous",
            spread: "auto",
            snap: true
        });
        console.log('Renderer created');
    },

    setupEventListeners() {
        this.setupRelocationListener();
        this.setupResizeListener();
        this.setupRenderedListener();
        this.setupAttachedListener();
        this.setupDisplayedListener();
        this.setupBookReadyListener();
    },

    setupRelocationListener() {
        AppState.rendition.on("relocated", (location) => {
            console.log('Page relocated:', location);
            AndroidBridge.onPageChanged(JSON.stringify(location));
            UIManager.highlightCurrentChapter(location.end.href);
        });
    },

    setupResizeListener() {
        AppState.rendition.on("resized", (size) => {
            console.log('Renderer resized:', size);
        });
    },

    setupRenderedListener() {
        AppState.rendition.on("rendered", (section, view) => {
            console.log('Section rendered:', section.href);
            this.setupDoubleClickHandler(view);
        });
    },

    setupDoubleClickHandler(view) {
        const contents = view.contents;
        if (contents && contents.window) {
            contents.window.addEventListener('dblclick', (e) => {
                console.log('Double click detected!');
                const navPanel = document.getElementById('nav-panel');
                if (navPanel.style.display === 'block') { return; }
                
                AndroidBridge.onDoubleClick();
                e.preventDefault();
            });
        }
    },

    setupAttachedListener() {
        AppState.rendition.on("attached", () => {
            AppState.rendition.manager.on("scroll", (position) => {
                console.log('View scroll');
                try {
                    const location = AppState.rendition.currentLocation();
                    if (location) {
                        console.log('Current location:', JSON.stringify(location));
                        AndroidBridge.onPageChanged(JSON.stringify(location));
                        UIManager.highlightCurrentChapter(location.end.href);
                    }
                } catch (err) {
                    console.error('Error in scroll event:', err.stack);
                }
            });
        });
    },

    setupDisplayedListener() {
        AppState.rendition.on("displayed", (section) => {
            console.log('Section displayed:', section.href);
            UIManager.highlightCurrentChapter(section.href);
        });
    },

    setupBookReadyListener() {
        AppState.book.ready.then(() => {
            console.log('Book ready');
            return AppState.book.loaded.metadata;
        }).then((metadata) => {
            console.log('Book metadata loaded:', metadata);
        }).catch((error) => {
            console.error('Book ready error:', error);
        });
    },

    loadNavigation() {
        AppState.book.loaded.navigation.then((toc) => {
            console.log('Navigation loaded');
            AppState.tableOfContents = toc.toc || [];
            UIManager.generateTOC(AppState.tableOfContents);
            UIManager.showTocButton();
        }).catch((err) => {
            console.error('Failed to load navigation:', err);
        });
    },

    generateLocationsAsync() {
        AppState.book.ready.then(() => {
            console.log('Generating locations asynchronously...');
            return AppState.book.locations.generate(1024);
        }).then(() => {
            AppState.isGeneratedLocations = true;
            console.log('Locations generated');
            if (AppState.isLoaded) {
                this.notifyPageChanged();
            }
        }).catch((err) => {
            console.error('Location generation error:', err);
        });
    },

    displayBook(startCfi) {
        console.log('Displaying book...', startCfi ? 'at saved position: ' + startCfi : '(first page)');

        let displayCfi = null;
        if (startCfi && typeof startCfi === 'string' && startCfi.trim() !== '') {
            if (startCfi.startsWith('epubcfi(')) {
                console.log('Valid CFI format detected:', startCfi);
                displayCfi = startCfi;
            } else {
                console.warn('Invalid CFI format:', startCfi, '- will show first page instead');
            }
        }

        const displayPromise = displayCfi 
            ? AppState.rendition.display(displayCfi)
            : AppState.rendition.display();

        displayPromise.then(() => {
            console.log('Book displayed successfully');
            UIManager.hideLoading();
            AppState.isLoaded = true;

            this.hookMappingFunctions();
            AndroidBridge.onLoadComplete();
            
            if (AppState.isGeneratedLocations) {
                this.notifyPageChanged();
            }
        }).catch((error) => {
            console.error('Display error:', error);
            UIManager.showError("显示书籍失败: " + error.message);
        });
    },

    notifyPageChanged() {
        try {
            const location = AppState.rendition.currentLocation();
            if (location && location.start && location.end) {
                const jsonLocation = JSON.stringify(location);
                console.log('Current location available:', jsonLocation);
                AndroidBridge.onPageChanged(jsonLocation);
            } else {
                console.warn('No current location available yet');
            }
        } catch (err) {
            console.error('Error getting current location:', err.stack);
        }
    },

    hookMappingFunctions() {
        if (AppState.rendition && AppState.rendition.manager) {
            const originalUpdateLayout = AppState.rendition.manager.updateLayout.bind(AppState.rendition.manager);
            AppState.rendition.manager.updateLayout = function() {
                originalUpdateLayout();
                if (this.mapping) {
                    const mapping = AppState.rendition.manager.mapping;
                    console.log('Replacing mapping functions ...');
                    mapping.findTextStartRange = (node, start, end) => {
                        return cus.findTextStartRangeByChar.call(mapping, node, start, end);
                    };
                    mapping.findTextEndRange = (node, start, end) => {
                        return cus.findTextEndRangeByChar.call(mapping, node, start, end);
                    };
                    mapping.splitTextNodeIntoRanges = (node) => {
                        return cus.splitTextNodeIntoCharRanges.call(mapping, node);
                    };
                }
            };
        }
    }
};

// ============================================
// 页面操作模块
// ============================================
const PageOperations = {
    goToLocation(cfi) {
        console.log('Going to location:', cfi);
        if (AppState.rendition) {
            AppState.rendition.display(cfi).then(() => {
                console.log('Location displayed');
            }).catch((error) => {
                console.error('Failed to go to location:', error);
            });
        }
    },

    prevPage() {
        console.log('Going to previous page');
        if (AppState.rendition) {
            AppState.rendition.prev().then(() => {
                AndroidBridge.onPageActionComplete('prev complete');
            }).catch((error) => {
                console.error('Failed to go to previous page:', error);
            });
        }
    },

    nextPage() {
        console.log('Going to next page');
        AppState.rendition.next().then(() => {
            AndroidBridge.onPageActionComplete('next complete');
        }).catch((error) => {
            console.error('Failed to go to next page:', error);
        });
    },

    goToPercentage(percentage) {
        console.log('Going to percentage:', percentage);
        if (AppState.book && AppState.rendition && percentage >= 0 && percentage <= 1) {
            const cfi = AppState.book.locations.cfiFromPercentage(percentage);
            if (cfi) {
                console.log('Generated CFI:', cfi);
                AppState.rendition.display(cfi).then(() => {
                    AndroidBridge.onPageActionComplete('go to percentage complete');
                }).catch((error) => {
                    console.error('Failed to go to percentage location:', error);
                });
            }
        } else {
            console.error('Invalid percentage value');
        }
    },

    getCurrentPageText() {
        let result = '';
        try {
            const location = AppState.rendition.currentLocation();
            if (location && location.start && location.end) {
                const views = AppState.rendition.manager ? AppState.rendition.manager.visible() : [];

                if (views && views.length > 0) {
                    views.forEach(view => {
                        if (view && view.contents) {
                            try {
                                const startRange = view.contents.range(location.start.cfi);
                                const endRange = view.contents.range(location.end.cfi);
                                
                                if (startRange && endRange) {
                                    const pageRange = view.contents.document.createRange();
                                    pageRange.setStart(startRange.startContainer, startRange.startOffset);
                                    pageRange.setEnd(endRange.endContainer, endRange.endOffset);
                                    const text = pageRange.toString();
                                    result += text;
                                }
                            } catch (e) {
                                console.error('Error getting range:', e);
                            }
                        }
                    });
                }
            }
        } catch (error) {
            console.error('Error in getCurrentPageText:', error);
        }

        if (AndroidBridge && AndroidBridge.onPageTextRetrieved) {
            AndroidBridge.onPageTextRetrieved(result);
        }
    },

    getCurrentLocation() {
        const location = AppState.rendition.currentLocation();
        if (location) {
            console.log('Getting current location:', location);
            if (AndroidBridge && AndroidBridge.onLocationRetrieved) {
                AndroidBridge.onLocationRetrieved(JSON.stringify(location));
            }
        } else {
            console.warn('No current location available');
            if (AndroidBridge && AndroidBridge.onLocationRetrieved) {
                AndroidBridge.onLocationRetrieved('{}');
            }
        }
    }
};

// ============================================
// 章节管理模块
// ============================================
const ChapterManager = {
    jumpToChapter(href) {
        console.log('Jumping to chapter:', href);

        if (AppState.rendition) {
            UIManager.closeNavPanel();

            setTimeout(() => {
                AppState.rendition.display(href).then(() => {
                    UIManager.highlightCurrentChapter(href);
                    console.log('Chapter displayed:', href);
                }).catch((error) => {
                    console.error('Failed to jump to chapter:', error);
                });
            }, 150);
        }
    }
};

// ============================================
// 搜索功能模块
// ============================================
const SearchManager = {
    async searchInBook(book, query) {
        if (AppState.isSearching) {
            console.log('正在搜索中...');
            return;
        }

        AppState.isSearching = true;
        console.log('开始搜索：' + query);

        try {
            await book.ready;
            const spineItems = book.spine.spineItems;
            console.log('spine size: ' + spineItems.length);

            for (let i = 0; i < spineItems.length; i++) {
                if (AppState.isSearching === false) {
                    console.log('取消搜索');
                    break;
                }
                const section = spineItems[i];
                console.log('searching in section href: ' + section.href);

                try {
                    let chapterTitle = '';
                    try {
                        const navItem = book.navigation.get(section.href);
                        chapterTitle = navItem ? navItem.label : '';
                    } catch (error) {
                        console.error('获取章节标题失败：', error);
                    }

                    const contents = await section.load(book.load.bind(book));
                    const matches = section.search(query);
                    console.log('matches size: ' + matches.length);

                    for (let matchIndex = 0; matchIndex < matches.length; matchIndex++) {
                        if (AppState.isSearching === false) {
                            console.log('取消搜索');
                            break;
                        }
                        const match = matches[matchIndex];
                        
                        const result = {
                            href: section.href,
                            sectionIndex: section.index,
                            cfi: match.cfi,
                            excerpt: match.excerpt,
                            chapterTitle: chapterTitle,
                            idref: section.idref,
                            matchIndex: matchIndex,
                            sectionBaseCfi: section.cfiBase,
                            position: match.position,
                            query: query
                        };

                        if (AndroidBridge && AndroidBridge.onSearchingResult) {
                            AndroidBridge.onSearchingResult(JSON.stringify(result));
                        }
                    }
                    section.unload();
                } catch (err) {
                    console.error('section load error:', err.stack);
                }
            }
        } catch (err) {
            console.error('book ready error:', err.stack);
        } finally {
            console.log('search completed');

            const callback = AppState.handleSearchCompleted;
            AppState.handleSearchCompleted = null;

            if (AndroidBridge && AndroidBridge.onSearchCompleted) {
                AndroidBridge.onSearchCompleted();
            }

            AppState.isSearching = false;

            if (callback) {
                try {
                    callback();
                } catch (e) {
                    console.error('Error calling search complete callback:', e);
                }
            }
        }
    },

    stopSearchAndWait() {
        if (AppState.isSearching) {
            console.log('取消当前搜索...');

            const promise = new Promise((resolve) => {
                AppState.handleSearchCompleted = resolve;
            });
            AppState.isSearching = false;
            return promise;
        } else {
            console.log('没有正在进行的搜索');
            return Promise.resolve();
        }
    },

    async search(query) {
        await this.stopSearchAndWait();
        this.searchInBook(AppState.book, query);
    }
};

// ============================================
// 高亮功能模块
// ============================================
const HighlightManager = {
    /**
     * 从 CSS 变量获取高亮颜色
     */
    getHighlightColor() {
        const computedStyle = getComputedStyle(document.documentElement);
        return computedStyle.getPropertyValue('--color-highlight').trim() || 'rgb(72, 72, 72)';
    },

    highlight(cfi, isRemove) {
        if (AppState.rendition) {
            if (isRemove) {
                console.log('remove highlight: ' + cfi);
                AppState.rendition.annotations.remove(cfi, "highlight");
            } else {
                console.log('add highlight: ' + cfi);

                // ✅ 从 CSS 变量动态获取高亮颜色
                const highlightColor = this.getHighlightColor();

                AppState.rendition.annotations.highlight(
                    cfi,
                    { color: "gray" },
                    null,
                    "my-highlight",
                    {
                        "fill": highlightColor,
                        "mix-blend-mode": "multiply"
                    }
                );
            }
        } else {
            console.error('rendition not ready');
        }
    }
};

// ============================================
// 初始化
// ============================================
window.onload = function() {
    AndroidBridge.onHtmlReady();
    UIManager.init();
};

window.onunload = function() {
    ResourceManager.cleanUp();
};

// 点击背景关闭导航面板
document.addEventListener('DOMContentLoaded', function() {
    const navPanel = document.getElementById('nav-panel');
    if (navPanel) {
        navPanel.addEventListener('click', function(e) {
            if (e.target === navPanel) {
                UIManager.closeNavPanel();
            }
        });
    }
});

// ============================================
// 暴露给 Android 的接口
// ============================================
window.EpubReader = {
    init: ReaderCore.init.bind(ReaderCore),
    goToLocation: PageOperations.goToLocation.bind(PageOperations),
    prevPage: PageOperations.prevPage.bind(PageOperations),
    nextPage: PageOperations.nextPage.bind(PageOperations),
    goToPercentage: PageOperations.goToPercentage.bind(PageOperations),
    cleanUp: ResourceManager.cleanUp.bind(ResourceManager),
    getCurrentPageText: PageOperations.getCurrentPageText.bind(PageOperations),
    getCurrentLocation: PageOperations.getCurrentLocation.bind(PageOperations),
    openNavPanel: UIManager.openNavPanel.bind(UIManager),
    closeNavPanel: UIManager.closeNavPanel.bind(UIManager),
    toggleNavPanel: UIManager.toggleNavPanel.bind(UIManager),
    search: SearchManager.search.bind(SearchManager),
    highlight: HighlightManager.highlight.bind(HighlightManager)
};
