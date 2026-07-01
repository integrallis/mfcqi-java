// MFCQI landing — theme switch, nav state, scroll reveal, copy-to-clipboard.

document.addEventListener('DOMContentLoaded', () => {
    // ---- Theme toggle (persisted; defaults to system preference) ----------
    const root = document.documentElement;
    const toggle = document.getElementById('theme-toggle');
    if (toggle) {
        toggle.addEventListener('click', () => {
            const next = root.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
            root.setAttribute('data-theme', next);
            try { localStorage.setItem('mfcqi-theme', next); } catch (e) { /* ignore */ }
        });
    }

    // ---- Nav scroll state -------------------------------------------------
    const nav = document.querySelector('.nav');
    const onScroll = () => nav.classList.toggle('scrolled', window.pageYOffset > 40);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });

    // ---- Smooth anchor scroll --------------------------------------------
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            const target = document.querySelector(this.getAttribute('href'));
            if (target) {
                e.preventDefault();
                const top = target.getBoundingClientRect().top + window.pageYOffset - 76;
                window.scrollTo({ top, behavior: 'smooth' });
            }
        });
    });

    // ---- Scroll reveal ----------------------------------------------------
    const revealEls = document.querySelectorAll('.reveal');
    const reveal = () => {
        const h = window.innerHeight;
        revealEls.forEach(el => {
            if (el.getBoundingClientRect().top < h - 110) el.classList.add('visible');
        });
    };
    reveal();
    let ticking = false;
    window.addEventListener('scroll', () => {
        if (!ticking) {
            window.requestAnimationFrame(() => { reveal(); ticking = false; });
            ticking = true;
        }
    }, { passive: true });

    // ---- Tabbed install widget -------------------------------------------
    const COMMANDS = {
        brew:   { copy: 'brew install integrallis/tap/mfcqi', show: 'brew install integrallis/tap/mfcqi', prompt: true },
        script: { copy: 'curl -fsSL https://raw.githubusercontent.com/integrallis/mfcqi-java/main/install.sh | sh', show: 'curl -fsSL https://raw.githubusercontent.com/integrallis/mfcqi-java/main/install.sh | sh', prompt: true },
        scoop:  { copy: 'scoop bucket add integrallis https://github.com/integrallis/scoop-bucket; scoop install mfcqi', show: 'scoop install mfcqi', prompt: true },
        maven:  { copy: 'implementation("com.integrallis:mfcqi-core:0.5.0")', show: 'implementation("com.integrallis:mfcqi-core:0.5.0")', prompt: false },
    };
    const tabs = document.querySelectorAll('.install-tab');
    const cmdText = document.getElementById('install-text');
    const cmdPrompt = document.getElementById('install-prompt');
    const copyBtn = document.getElementById('install-copy');
    let current = 'brew';

    const renderCmd = key => {
        const c = COMMANDS[key];
        current = key;
        if (cmdText) cmdText.textContent = c.show;
        if (cmdPrompt) cmdPrompt.style.display = c.prompt ? 'inline' : 'none';
    };
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            renderCmd(tab.dataset.target);
        });
    });
    if (copyBtn) {
        copyBtn.addEventListener('click', () => {
            navigator.clipboard.writeText(COMMANDS[current].copy).then(() => {
                copyBtn.classList.add('copied');
                setTimeout(() => copyBtn.classList.remove('copied'), 1500);
            });
        });
    }
});
