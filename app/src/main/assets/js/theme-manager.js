// ============================================
// 主题管理器 - 动态加载主题 CSS
// ============================================
const ThemeManager = {
    // 当前激活的主题
    currentTheme: 'light',
    
    // 主题配置文件路径
    themeFiles: {
        'light': 'css/theme-light.css',
        'dark': 'css/theme-dark.css',
        'eye-care': 'css/theme-eye-care.css'
    },
    
    // 主题样式标签 ID
    themeStyleId: 'dynamic-theme-style',
    
    /**
     * 初始化主题管理器
     */
    init() {
        // 从 localStorage 读取上次使用的主题
        const savedTheme = this.getSavedTheme();
        if (savedTheme && this.themeFiles[savedTheme]) {
            this.loadTheme(savedTheme);
        } else {
            // 默认使用浅色主题
            this.loadTheme('light');
        }
    },
    
    /**
     * 加载指定主题
     * @param {string} themeName - 主题名称 ('light', 'dark', 'eye-care')
     */
    loadTheme(themeName) {
        if (!this.themeFiles[themeName]) {
            console.error(`Theme "${themeName}" not found`);
            return false;
        }
        
        console.log(`Loading theme: ${themeName}`);
        
        // 移除旧的主题样式
        this.removeCurrentTheme();
        
        // 创建新的 link 标签加载主题 CSS
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.type = 'text/css';
        link.href = this.themeFiles[themeName];
        link.id = this.themeStyleId;
        
        // 监听加载完成
        link.onload = () => {
            console.log(`Theme "${themeName}" loaded successfully`);
            this.currentTheme = themeName;
            this.saveTheme(themeName);
            
            // 通知 Android 主题已切换（可选）
            if (window.Android && window.Android.onThemeChanged) {
                window.Android.onThemeChanged(themeName);
            }
        };
        
        link.onerror = () => {
            console.error(`Failed to load theme: ${themeName}`);
        };
        
        // 添加到 head
        document.head.appendChild(link);
        
        return true;
    },
    
    /**
     * 移除当前主题样式
     */
    removeCurrentTheme() {
        const existingStyle = document.getElementById(this.themeStyleId);
        if (existingStyle) {
            existingStyle.remove();
            console.log('Previous theme removed');
        }
    },
    
    /**
     * 切换到下一个主题
     */
    toggleTheme() {
        const themes = Object.keys(this.themeFiles);
        const currentIndex = themes.indexOf(this.currentTheme);
        const nextIndex = (currentIndex + 1) % themes.length;
        const nextTheme = themes[nextIndex];
        
        this.loadTheme(nextTheme);
        return nextTheme;
    },
    
    /**
     * 获取当前主题
     */
    getCurrentTheme() {
        return this.currentTheme;
    },
    
    /**
     * 保存主题偏好到 localStorage
     */
    saveTheme(themeName) {
        try {
            localStorage.setItem('epub-reader-theme', themeName);
        } catch (e) {
            console.warn('Failed to save theme preference:', e);
        }
    },
    
    /**
     * 从 localStorage 读取保存的主题
     */
    getSavedTheme() {
        try {
            return localStorage.getItem('epub-reader-theme');
        } catch (e) {
            console.warn('Failed to read theme preference:', e);
            return null;
        }
    },
    
    /**
     * 获取所有可用主题列表
     */
    getAvailableThemes() {
        return Object.keys(this.themeFiles);
    },
    
    /**
     * 通过 Android 接口设置主题
     * @param {string} themeName - 主题名称
     */
    setThemeFromAndroid(themeName) {
        console.log('Setting theme from Android:', themeName);
        if (this.themeFiles[themeName]) {
            this.loadTheme(themeName);
            return true;
        }
        return false;
    }
};

// ============================================
// 在页面加载时初始化主题
// ============================================
document.addEventListener('DOMContentLoaded', function() {
    ThemeManager.init();
});

// ============================================
// 暴露给 Android 的接口
// ============================================
if (typeof window.EpubReader !== 'undefined') {
    window.EpubReader.setTheme = ThemeManager.setThemeFromAndroid.bind(ThemeManager);
    window.EpubReader.toggleTheme = ThemeManager.toggleTheme.bind(ThemeManager);
    window.EpubReader.getCurrentTheme = ThemeManager.getCurrentTheme.bind(ThemeManager);
}
