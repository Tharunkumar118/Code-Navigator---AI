/**
   CodeNavigator AI — Dashboard Logic Layer
   Vanilla SPA Routing, Authentication, Polling, and SVG Graph Canvas
 */

const BASE_URL = 'http://localhost:8081/api/v1';

// Global Client State
const state = {
    token: localStorage.getItem('jwt_token'),
    user: null,
    repos: [],
    activeRepoId: null,
    currentView: 'repos',
    isSignUp: false,
    analysisIntervals: {},
    diagrams: null,
    currentDiagramType: 'class',
    analysisCache: {
        ARCHITECTURE: null,
        SOURCE_CODE: null,
        API_DOCUMENTATION: null,
        DATABASE: null,
        CODE_QUALITY: null,
        SECURITY: null
    },
    zooms: {
        arch: 1.0,
        db: 1.0
    }
};

// Initializer
document.addEventListener('DOMContentLoaded', () => {
    initElements();
    initEventListeners();
    checkAuth();
});

// Elements Cache
let el = {};
function initElements() {
    el = {
        authOverlay: document.getElementById('authOverlay'),
        authForm: document.getElementById('authForm'),
        authTitle: document.getElementById('authTitle'),
        authSubtitle: document.getElementById('authSubtitle'),
        authSubmitBtn: document.getElementById('authSubmitBtn'),
        authToggleText: document.getElementById('authToggleText'),
        authToggleLink: document.getElementById('authToggleLink'),
        regFullNameGroup: document.getElementById('regFullNameGroup'),
        fullName: document.getElementById('fullName'),
        email: document.getElementById('email'),
        password: document.getElementById('password'),
        
        appContainer: document.getElementById('appContainer'),
        sidebarNav: document.querySelector('.sidebar-nav'),
        logoutBtn: document.getElementById('logoutBtn'),
        userInitial: document.getElementById('userInitial'),
        userDisplayName: document.getElementById('userDisplayName'),
        userDisplayEmail: document.getElementById('userDisplayEmail'),
        
        pageTitle: document.getElementById('pageTitle'),
        pageSubtitle: document.getElementById('pageSubtitle'),
        activeRepoSelectorContainer: document.getElementById('activeRepoSelectorContainer'),
        activeRepoSelect: document.getElementById('activeRepoSelect'),
        
        repoForm: document.getElementById('repoForm'),
        repoUrl: document.getElementById('repoUrl'),
        reposTable: document.getElementById('reposTable').querySelector('tbody'),
        
        // Architecture elements
        triggerArchBtn: document.getElementById('triggerArchBtn'),
        archStatusText: document.getElementById('archStatusText'),
        packageTreeContainer: document.getElementById('packageTreeContainer'),
        graphCanvas: document.getElementById('graphCanvas'),
        
        // Metrics elements
        triggerMetricsBtn: document.getElementById('triggerMetricsBtn'),
        metricsStatusText: document.getElementById('metricsStatusText'),
        metricClassesCount: document.getElementById('metricClassesCount'),
        metricMethodsCount: document.getElementById('metricMethodsCount'),
        metricLinesOfCode: document.getElementById('metricLinesOfCode'),
        metricAvgComplexity: document.getElementById('metricAvgComplexity'),
        inheritanceTreeContainer: document.getElementById('inheritanceTreeContainer'),
        classesContainer: document.getElementById('classesContainer'),
        classSearchInput: document.getElementById('classSearchInput'),
        
        // API elements
        triggerApiBtn: document.getElementById('triggerApiBtn'),
        apiStatusText: document.getElementById('apiStatusText'),
        endpointsListContainer: document.getElementById('endpointsListContainer'),
        openapiSpecContainer: document.getElementById('openapiSpecContainer'),
        openapiRawCode: document.getElementById('openapiRawCode'),
        
        // Database elements
        triggerDbBtn: document.getElementById('triggerDbBtn'),
        dbStatusText: document.getElementById('dbStatusText'),
        dbTablesCount: document.getElementById('dbTablesCount'),
        dbRelationsCount: document.getElementById('dbRelationsCount'),
        erDiagramContainer: document.getElementById('erDiagramContainer'),
        dbSummaryContainer: document.getElementById('dbSummaryContainer'),
        
        // Code Quality elements
        triggerQualityBtn: document.getElementById('triggerQualityBtn'),
        qualityStatusText: document.getElementById('qualityStatusText'),
        qualityScoreCircle: document.getElementById('qualityScoreCircle'),
        qualityScoreText: document.getElementById('qualityScoreText'),
        qualityGradeText: document.getElementById('qualityGradeText'),
        qualityCriticalCount: document.getElementById('qualityCriticalCount'),
        qualityWarningCount: document.getElementById('qualityWarningCount'),
        qualityInfoCount: document.getElementById('qualityInfoCount'),
        qualityIssuesContainer: document.getElementById('qualityIssuesContainer'),
        
        // Security elements
        triggerSecurityBtn: document.getElementById('triggerSecurityBtn'),
        securityStatusText: document.getElementById('securityStatusText'),
        securityScoreText: document.getElementById('securityScoreText'),
        securityCriticalCount: document.getElementById('securityCriticalCount'),
        securityHighCount: document.getElementById('securityHighCount'),
        securityMediumCount: document.getElementById('securityMediumCount'),
        securityIssuesContainer: document.getElementById('securityIssuesContainer'),
        
        toastContainer: document.getElementById('toastContainer')
    };
    lucide.createIcons();
}

// Event bindings
function initEventListeners() {
    // Auth Toggle
    el.authToggleLink.addEventListener('click', (e) => {
        e.preventDefault();
        state.isSignUp = !state.isSignUp;
        toggleAuthMode();
    });

    // Auth Submission
    el.authForm.addEventListener('submit', handleAuthSubmit);

    // Logout
    el.logoutBtn.addEventListener('click', handleLogout);

    // Sidebar View Swap
    el.sidebarNav.addEventListener('click', (e) => {
        const item = e.target.closest('.nav-item');
        if (!item) return;
        e.preventDefault();
        
        document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
        item.classList.add('active');
        
        const view = item.getAttribute('data-view');
        switchView(view);
    });

    // Repository Form Submission
    el.repoForm.addEventListener('submit', handleRepoSubmit);

    // Active Repository dropdown change
    el.activeRepoSelect.addEventListener('change', (e) => {
        const id = parseInt(e.target.value);
        setActiveRepository(id);
    });

    // Trigger analysis commands
    el.triggerArchBtn.addEventListener('click', () => triggerAnalysis('ARCHITECTURE'));
    el.triggerMetricsBtn.addEventListener('click', () => triggerAnalysis('SOURCE_CODE'));
    el.triggerApiBtn.addEventListener('click', () => triggerAnalysis('API_DOCUMENTATION'));
    el.triggerDbBtn.addEventListener('click', () => triggerAnalysis('DATABASE'));
    el.triggerQualityBtn.addEventListener('click', () => triggerAnalysis('CODE_QUALITY'));
    el.triggerSecurityBtn.addEventListener('click', () => triggerAnalysis('SECURITY'));

    // Export Dropdown menu toggle
    const exportMenuBtn = document.getElementById('exportMenuBtn');
    const exportDropdownMenu = document.getElementById('exportDropdownMenu');
    if (exportMenuBtn && exportDropdownMenu) {
        exportMenuBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            exportDropdownMenu.classList.toggle('hidden');
        });
        document.addEventListener('click', () => {
            exportDropdownMenu.classList.add('hidden');
        });
    }

    // Search filter for source code class files
    el.classSearchInput.addEventListener('input', filterClassCatalog);
}

// Visual auth updates
function toggleAuthMode() {
    if (state.isSignUp) {
        el.authTitle.innerText = "Create Account";
        el.authSubtitle.innerText = "Get started with CodeNavigator AI";
        el.authSubmitBtn.querySelector('span').innerText = "Register";
        el.authToggleText.innerText = "Already have an account?";
        el.authToggleLink.innerText = "Sign In";
        el.regFullNameGroup.style.display = "block";
        el.fullName.setAttribute('required', 'true');
    } else {
        el.authTitle.innerText = "Sign In";
        el.authSubtitle.innerText = "Access your code intelligence panel";
        el.authSubmitBtn.querySelector('span').innerText = "Sign In";
        el.authToggleText.innerText = "Don't have an account?";
        el.authToggleLink.innerText = "Sign Up";
        el.regFullNameGroup.style.display = "none";
        el.fullName.removeAttribute('required');
    }
    lucide.createIcons();
}

// Authentication verification
async function checkAuth() {
    if (!state.token) {
        showAuthOverlay(true);
        return;
    }
    
    try {
        const res = await apiFetch('/users/me');
        if (res.success) {
            state.user = res.data;
            showAuthOverlay(false);
            updateUserBadge();
            await fetchRepositories();
        } else {
            showAuthOverlay(true);
        }
    } catch (err) {
        console.error("Auth check failed", err);
        showAuthOverlay(true);
    }
}

function showAuthOverlay(show) {
    if (show) {
        el.authOverlay.classList.remove('hidden');
        el.appContainer.classList.add('hidden');
    } else {
        el.authOverlay.classList.add('hidden');
        el.appContainer.classList.remove('hidden');
        lucide.createIcons();
    }
}

// Auth submission handler
async function handleAuthSubmit(e) {
    e.preventDefault();
    const endpoint = state.isSignUp ? '/auth/register' : '/auth/login';
    const body = {
        email: el.email.value,
        password: el.password.value
    };
    if (state.isSignUp) {
        body.fullName = el.fullName.value;
    }

    try {
        const res = await apiFetch(endpoint, 'POST', body);
        if (res.success) {
            showToast(state.isSignUp ? "Registration successful!" : "Welcome back!", 'success');
            
            if (state.isSignUp) {
                state.isSignUp = false;
                toggleAuthMode();
            }

            state.token = res.data.accessToken;
            localStorage.setItem('jwt_token', state.token);
            el.password.value = '';
            await checkAuth();
        } else {
            showToast(res.message || "Authentication error", 'error');
        }
    } catch (err) {
        showToast("Server connection failed", 'error');
    }
}

function handleLogout() {
    localStorage.removeItem('jwt_token');
    state.token = null;
    state.user = null;
    state.repos = [];
    state.activeRepoId = null;
    
    // Clear intervals
    Object.values(state.analysisIntervals).forEach(clearInterval);
    state.analysisIntervals = {};

    showAuthOverlay(true);
    showToast("Signed out successfully", 'info');
}

function updateUserBadge() {
    if (!state.user) return;
    el.userDisplayName.innerText = state.user.fullName;
    el.userDisplayEmail.innerText = state.user.email;
    el.userInitial.innerText = state.user.fullName.charAt(0).toUpperCase();
}

// Views Navigation Router
function switchView(viewName) {
    state.currentView = viewName;
    
    // Toggle active screen panel
    document.querySelectorAll('.view-section').forEach(sec => sec.classList.add('hidden'));
    document.getElementById(`view-${viewName}`).classList.remove('hidden');
    
    // Set headers
    const meta = {
        repos: { title: 'Repositories', desc: 'Scan and clone GitHub repository codebases' },
        architecture: { title: 'Architectural Analysis', desc: 'Package mappings and dependency relationships' },
        metrics: { title: 'Source Code Intelligence', desc: 'Cyclomatic complexity, Line metrics, and inheritance trees' },
        apidocs: { title: 'API Documentation Spec', desc: 'Auto-generated OpenAPI 3.0 specification catalogs' },
        database: { title: 'Database Intelligence', desc: 'Entity relationships schema mapping and data constraints dictionary' },
        quality: { title: 'Code Quality Analyzer', desc: 'Detect code smells, variable scopes, violations, and duplicates' },
        security: { title: 'Security Analyzer', desc: 'Detect SQL injection, hardcoded secrets, weak JWT keys, and unprotected routes' },
        chat: { title: 'AI Codebase Assistant', desc: 'Conversational chat portal to query software files structure and quality logs' }
    };
    
    el.pageTitle.innerText = meta[viewName].title;
    el.pageSubtitle.innerText = meta[viewName].desc;
    
    if (viewName !== 'repos' && state.activeRepoId) {
        loadAnalysisForRepo(state.activeRepoId, viewName);
    }
    
    // Draw/Redraw diagrams when their views become active and visible in DOM
    if (viewName === 'database') {
        setTimeout(() => {
            if (window.drawDatabaseErDiagram) window.drawDatabaseErDiagram();
        }, 150);
    } else if (viewName === 'architecture') {
        const panelDiagram = document.getElementById('archDiagramsPanel');
        if (panelDiagram && !panelDiagram.classList.contains('hidden')) {
            setTimeout(() => {
                if (window.renderSelectedMermaid) window.renderSelectedMermaid();
            }, 150);
        }
    }
    
    lucide.createIcons();
}

// Fetch lists of repos
async function fetchRepositories() {
    try {
        const res = await apiFetch('/repositories');
        if (res.success) {
            state.repos = res.data;
            renderReposTable();
            renderActiveRepoSelect();
            
            // Set first active repo if none selected
            if (state.repos.length > 0 && !state.activeRepoId) {
                setActiveRepository(state.repos[0].id);
            }
        }
    } catch (err) {
        showToast("Failed to fetch repositories", 'error');
    }
}

// Submit a repository clone scan
async function handleRepoSubmit(e) {
    e.preventDefault();
    const url = el.repoUrl.value;
    try {
        const res = await apiFetch('/repositories', 'POST', { repoUrl: url });
        if (res.success) {
            showToast("Repository scan queued successfully!", 'success');
            el.repoUrl.value = '';
            await fetchRepositories();
            
            // Start checking status dynamically
            pollRepositoryScan(res.data.id);
        } else {
            showToast(res.message || "Failed to submit repository", 'error');
        }
    } catch (err) {
        showToast("Server connection error", 'error');
    }
}

function pollRepositoryScan(id) {
    const timer = setInterval(async () => {
        try {
            const res = await apiFetch(`/repositories`);
            if (res.success) {
                const repo = res.data.find(r => r.id === id);
                if (repo) {
                    if (repo.status === 'COMPLETED') {
                        clearInterval(timer);
                        showToast(`Repository ${repo.repoName} successfully cloned!`, 'success');
                        await fetchRepositories();
                    } else if (repo.status === 'FAILED') {
                        clearInterval(timer);
                        showToast(`Cloning repository ${repo.repoName} failed!`, 'error');
                        await fetchRepositories();
                    }
                }
            }
        } catch (e) {
            clearInterval(timer);
        }
    }, 4000);
}

function renderReposTable() {
    if (state.repos.length === 0) {
        el.reposTable.innerHTML = `
            <tr>
                <td colspan="5" class="table-empty">No repositories registered yet. Add a GitHub URL to start!</td>
            </tr>
        `;
        return;
    }

    el.reposTable.innerHTML = state.repos.map(r => `
        <tr>
            <td style="font-weight: 600;">${escapeHtml(r.repoName)}</td>
            <td>${r.primaryLanguage ? escapeHtml(r.primaryLanguage) : '<span style="color: var(--text-muted);">Undetected</span>'}</td>
            <td>${r.sizeKb ? formatSize(r.sizeKb) : '-'}</td>
            <td>
                <span class="badge badge-${r.status.toLowerCase()}">${r.status}</span>
            </td>
            <td>
                <button onclick="selectAndGoToAnalysis(${r.id})" class="btn secondary-btn" style="padding: 6px 12px; font-size: 12px;" ${r.status !== 'COMPLETED' ? 'disabled' : ''}>
                    <i data-lucide="line-chart" style="width: 14px; height: 14px; display: inline; vertical-align: middle;"></i>
                    <span>Analyze</span>
                </button>
            </td>
        </tr>
    `).join('');
    lucide.createIcons();
}

function renderActiveRepoSelect() {
    const completedRepos = state.repos.filter(r => r.status === 'COMPLETED');
    
    if (completedRepos.length === 0) {
        el.activeRepoSelectorContainer.classList.add('hidden');
        return;
    }

    el.activeRepoSelectorContainer.classList.remove('hidden');
    el.activeRepoSelect.innerHTML = completedRepos.map(r => `
        <option value="${r.id}" ${state.activeRepoId === r.id ? 'selected' : ''}>
            ${escapeHtml(r.repoName)}
        </option>
    `).join('');
}

function setActiveRepository(id) {
    state.activeRepoId = id;
    renderActiveRepoSelect();
    
    // Clear cache
    state.analysisCache = {
        ARCHITECTURE: null,
        SOURCE_CODE: null,
        API_DOCUMENTATION: null,
        DATABASE: null,
        CODE_QUALITY: null,
        SECURITY: null
    };

    // Load full history results for all categories in background
    loadAllAnalysisHistoryForRepo(id);

    // Trigger reloading the analytical report for current active screen
    if (state.currentView !== 'repos') {
        loadAnalysisForRepo(id, state.currentView);
    }
}

async function loadAllAnalysisHistoryForRepo(repoId) {
    try {
        const res = await apiFetch(`/analyses/history/${repoId}`);
        if (res.success) {
            const types = ['ARCHITECTURE', 'SOURCE_CODE', 'API_DOCUMENTATION', 'DATABASE', 'CODE_QUALITY', 'SECURITY'];
            for (const type of types) {
                const history = res.data
                    .filter(a => a.type === type && a.status === 'COMPLETED')
                    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
                if (history.length > 0) {
                    const detailRes = await apiFetch(`/analyses/${history[0].id}`);
                    if (detailRes.success) {
                        state.analysisCache[type] = detailRes.data.result;
                    }
                }
            }
        }
    } catch (e) {
        console.error("Failed to load background history cache", e);
    }
}

window.selectAndGoToAnalysis = function(id) {
    setActiveRepository(id);
    switchView('architecture');
    document.querySelectorAll('.nav-item').forEach(n => {
        n.classList.remove('active');
        if (n.getAttribute('data-view') === 'architecture') n.classList.add('active');
    });
};

// Analysis triggers
async function triggerAnalysis(type) {
    if (!state.activeRepoId) {
        showToast("Please select a repository first.", 'error');
        return;
    }
    
    // Update status controls based on current type
    const statusMap = {
        ARCHITECTURE: { text: el.archStatusText, btn: el.triggerArchBtn },
        SOURCE_CODE: { text: el.metricsStatusText, btn: el.triggerMetricsBtn },
        API_DOCUMENTATION: { text: el.apiStatusText, btn: el.triggerApiBtn },
        DATABASE: { text: el.dbStatusText, btn: el.triggerDbBtn },
        CODE_QUALITY: { text: el.qualityStatusText, btn: el.triggerQualityBtn },
        SECURITY: { text: el.securityStatusText, btn: el.triggerSecurityBtn }
    };
    
    const ui = statusMap[type];
    ui.btn.disabled = true;
    ui.text.innerText = "Submitting analysis request...";

    try {
        const res = await apiFetch('/analyses', 'POST', {
            repositoryId: state.activeRepoId,
            type: type
        });
        
        if (res.success) {
            showToast("Analysis job submitted!", 'info');
            pollAnalysisStatus(res.data.id, type);
        } else {
            showToast(res.message || "Failed to trigger analysis", 'error');
            ui.btn.disabled = false;
            ui.text.innerText = "Analysis failed: " + (res.message || "");
        }
    } catch (e) {
        showToast("Server connection error", 'error');
        ui.btn.disabled = false;
        ui.text.innerText = "Connection failed";
    }
}

function pollAnalysisStatus(id, type) {
    const statusMap = {
        ARCHITECTURE: { text: el.archStatusText, btn: el.triggerArchBtn },
        SOURCE_CODE: { text: el.metricsStatusText, btn: el.triggerMetricsBtn },
        API_DOCUMENTATION: { text: el.apiStatusText, btn: el.triggerApiBtn },
        DATABASE: { text: el.dbStatusText, btn: el.triggerDbBtn },
        CODE_QUALITY: { text: el.qualityStatusText, btn: el.triggerQualityBtn },
        SECURITY: { text: el.securityStatusText, btn: el.triggerSecurityBtn }
    };
    const ui = statusMap[type];
    
    if (state.analysisIntervals[type]) {
        clearInterval(state.analysisIntervals[type]);
    }

    state.analysisIntervals[type] = setInterval(async () => {
        try {
            const res = await apiFetch(`/analyses/${id}`);
            if (res.success) {
                const record = res.data;
                ui.text.innerText = `Scan Status: ${record.status}`;
                
                if (record.status === 'COMPLETED') {
                    clearInterval(state.analysisIntervals[type]);
                    ui.btn.disabled = false;
                    ui.text.innerText = "Scan Status: COMPLETED";
                    showToast(`${type} analysis completed successfully!`, 'success');
                    
                    state.analysisCache[type] = record.result;
                    // Render analysis results directly!
                    renderAnalysisResult(record.result, type);
                } else if (record.status === 'FAILED') {
                    clearInterval(state.analysisIntervals[type]);
                    ui.btn.disabled = false;
                    ui.text.innerText = "Scan Status: FAILED";
                    showToast(`${type} analysis failed!`, 'error');
                }
            }
        } catch (e) {
            clearInterval(state.analysisIntervals[type]);
            ui.btn.disabled = false;
        }
    }, 2000);
}

// Fetch historical analysis details
async function loadAnalysisForRepo(repoId, viewName) {
    const type = viewName === 'architecture' ? 'ARCHITECTURE' : 
                 viewName === 'metrics' ? 'SOURCE_CODE' : 
                 viewName === 'apidocs' ? 'API_DOCUMENTATION' : 
                 viewName === 'database' ? 'DATABASE' : 
                 viewName === 'quality' ? 'CODE_QUALITY' : 'SECURITY';
                 
    const statusMap = {
        ARCHITECTURE: { text: el.archStatusText, btn: el.triggerArchBtn },
        SOURCE_CODE: { text: el.metricsStatusText, btn: el.triggerMetricsBtn },
        API_DOCUMENTATION: { text: el.apiStatusText, btn: el.triggerApiBtn },
        DATABASE: { text: el.dbStatusText, btn: el.triggerDbBtn },
        CODE_QUALITY: { text: el.qualityStatusText, btn: el.triggerQualityBtn },
        SECURITY: { text: el.securityStatusText, btn: el.triggerSecurityBtn }
    };
    const ui = statusMap[type];
    ui.text.innerText = "Checking analysis history...";
    ui.btn.disabled = false;
    
    // Clear display panes
    clearDisplays(viewName);

    try {
        const res = await apiFetch(`/analyses/history/${repoId}`);
        if (res.success) {
            // Find most recent completed record of this type
            const history = res.data
                .filter(a => a.type === type)
                .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
                
            if (history.length > 0) {
                const latest = history[0];
                ui.text.innerText = `Scan Status: ${latest.status}`;
                if (latest.status === 'COMPLETED') {
                    // Fetch full payload details
                    const detailRes = await apiFetch(`/analyses/${latest.id}`);
                    if (detailRes.success) {
                        state.analysisCache[type] = detailRes.data.result;
                        renderAnalysisResult(detailRes.data.result, type);
                    }
                }
            } else {
                ui.text.innerText = "No scan records found.";
            }
        }
    } catch (e) {
        ui.text.innerText = "Failed to load history.";
    }
}

function clearDisplays(viewName) {
    if (viewName === 'architecture') {
        el.packageTreeContainer.innerHTML = '<div class="empty-state">Trigger scan to view structures.</div>';
        el.graphCanvas.innerHTML = '<div class="empty-state">No architectural graph parsed.</div>';
        state.diagrams = null;
        document.getElementById('mermaidRenderCanvas').innerHTML = '<div class="empty-state">Scan needed to generate diagram drawings.</div>';
    } else if (viewName === 'metrics') {
        el.metricClassesCount.innerText = '-';
        el.metricMethodsCount.innerText = '-';
        el.metricLinesOfCode.innerText = '-';
        el.metricAvgComplexity.innerText = '-';
        el.inheritanceTreeContainer.innerHTML = '<div class="empty-state">Trigger scan to compile inheritance trees.</div>';
        el.classesContainer.innerHTML = '<div class="empty-state">Select class profile to display details.</div>';
    } else if (viewName === 'apidocs') {
        el.endpointsListContainer.innerHTML = '<div class="empty-state">Generate API documentation to list endpoints.</div>';
        el.openapiRawCode.innerText = 'Trigger analysis to compile specification JSON...';
    } else if (viewName === 'database') {
        el.dbTablesCount.innerText = '0';
        el.dbRelationsCount.innerText = '0';
        el.erDiagramContainer.innerHTML = '<div class="empty-state">Trigger analysis to compile database schema diagram.</div>';
        el.dbSummaryContainer.innerHTML = '<div class="empty-state">Trigger database scan to view schema dictionary.</div>';
    } else if (viewName === 'quality') {
        el.qualityScoreCircle.style.strokeDashoffset = '213.6';
        el.qualityScoreText.innerText = '100';
        el.qualityGradeText.innerText = 'Excellent';
        el.qualityCriticalCount.innerText = '0';
        el.qualityWarningCount.innerText = '0';
        el.qualityInfoCount.innerText = '0';
        el.qualityIssuesContainer.innerHTML = '<div class="empty-state">Trigger analysis to check codebase quality smells.</div>';
    } else if (viewName === 'security') {
        el.securityScoreText.innerText = '0 Vulnerabilities';
        el.securityCriticalCount.innerText = '0';
        el.securityHighCount.innerText = '0';
        el.securityMediumCount.innerText = '0';
        el.securityIssuesContainer.innerHTML = '<div class="empty-state">Trigger analysis to check codebase security vulnerabilities.</div>';
    }
}

// Main response router
function renderAnalysisResult(result, type) {
    if (!result) return;
    
    if (type === 'ARCHITECTURE') {
        renderArchitectureResult(result);
    } else if (type === 'SOURCE_CODE') {
        renderSourceCodeResult(result);
    } else if (type === 'API_DOCUMENTATION') {
        renderApiDocumentationResult(result);
    } else if (type === 'DATABASE') {
        renderDatabaseResult(result);
    } else if (type === 'CODE_QUALITY') {
        renderCodeQualityResult(result);
    } else if (type === 'SECURITY') {
        renderSecurityResult(result);
    }
}

// ----------------------------------------
// ARCHITECTURE RENDERERS
// ----------------------------------------
function renderArchitectureResult(result) {
    // Cache diagrams payload
    state.diagrams = result.diagrams || null;

    // 1. Render Package tree
    if (result.packageStructure) {
        el.packageTreeContainer.innerHTML = renderPackageTreeNode(result.packageStructure);
    }

    // 2. Render SVG Graph Layout (Force-directed node model)
    const graphData = result.dependencyGraph || result.moduleGraph || { nodes: [], edges: [] };
    renderDependencyGraphSvg(graphData);

    // 3. Render diagrams
    renderSelectedMermaid();
}

function renderPackageTreeNode(node) {
    if (!node) return '';
    const hasChildren = node.subPackages && node.subPackages.length > 0;
    
    let html = `<div class="tree-node">
        <span class="node-label">
            <i data-lucide="${hasChildren ? 'folder-open' : 'file-code'}"></i>
            <span>${escapeHtml(node.name)}</span>
        </span>`;
        
    if (hasChildren) {
        html += node.subPackages.map(renderPackageTreeNode).join('');
    }
    
    html += `</div>`;
    return html;
}

function renderDependencyGraphSvg(graph) {
    if (!graph.nodes || graph.nodes.length === 0) {
        el.graphCanvas.innerHTML = '<div class="empty-state">No package/module dependency links.</div>';
        return;
    }

    // Dynamic Node Coordinate Solver (spreads nodes in a force-directed simulation or clean radial ring)
    const width = 600;
    const height = 400;
    const padding = 60;
    
    // Assign radial position mapping
    const center = { x: width / 2, y: height / 2 };
    const radius = Math.min(width, height) / 2 - padding;
    
    const nodes = graph.nodes.map((n, i) => {
        const angle = (i * 2 * Math.PI) / graph.nodes.length;
        return {
            id: n.id,
            label: n.label || n.id.split('.').pop(),
            x: center.x + radius * Math.cos(angle),
            y: center.y + radius * Math.sin(angle)
        };
    });

    const nodeMap = new Map(nodes.map(n => [n.id, n]));

    // Reconstruct connection links
    const links = (graph.edges || []).map(e => {
        const sourceNode = nodeMap.get(e.source);
        const targetNode = nodeMap.get(e.target);
        if (sourceNode && targetNode) {
            return { source: sourceNode, target: targetNode };
        }
        return null;
    }).filter(l => l !== null);

    // Build SVG Elements
    let svgHtml = `<svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" style="background: transparent; max-width: 100%;">
        <!-- Arrowhead Marker -->
        <defs>
            <marker id="arrow" viewBox="0 0 10 10" refX="22" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                <path d="M 0 0 L 10 5 L 0 10 z" fill="#06b6d4" />
            </marker>
        </defs>
        
        <!-- Connection lines -->
        <g stroke="rgba(255,255,255,0.15)" stroke-width="1.5" fill="none" marker-end="url(#arrow)">`;
        
    links.forEach(l => {
        svgHtml += `<line x1="${l.source.x}" y1="${l.source.y}" x2="${l.target.x}" y2="${l.target.y}" stroke="#06b6d4" opacity="0.4"></line>`;
    });
        
    svgHtml += `</g>
        <!-- Nodes -->
        <g>`;
        
    nodes.forEach(n => {
        svgHtml += `<g class="svg-node" style="cursor: pointer;">
            <!-- Outer Glow -->
            <circle cx="${n.x}" cy="${n.y}" r="12" fill="rgba(6, 182, 212, 0.2)" stroke="#06b6d4" stroke-width="2"></circle>
            <!-- Core -->
            <circle cx="${n.x}" cy="${n.y}" r="6" fill="#06b6d4"></circle>
            <!-- Text Label -->
            <text x="${n.x}" y="${n.y + 26}" fill="#f3f4f6" font-size="11" font-family="monospace" font-weight="600" text-anchor="middle">
                ${escapeHtml(n.label)}
            </text>
        </g>`;
    });
        
    svgHtml += `</g>
    </svg>`;
    
    el.graphCanvas.innerHTML = svgHtml;
}

// ----------------------------------------
// SOURCE CODE METRICS RENDERERS
// ----------------------------------------
let cachedClassDetails = [];

function renderSourceCodeResult(result) {
    // 1. Hydrate numeric status widgets
    el.metricClassesCount.innerText = result.classesCount || 0;
    el.metricMethodsCount.innerText = result.methodsCount || 0;
    el.metricLinesOfCode.innerText = result.totalLinesOfCode || 0;
    el.metricAvgComplexity.innerText = result.averageCyclomaticComplexity ? result.averageCyclomaticComplexity.toFixed(2) : '0.00';

    // 2. Render Inheritance trees
    if (result.inheritanceTree && result.inheritanceTree.length > 0) {
        el.inheritanceTreeContainer.innerHTML = result.inheritanceTree.map(renderInheritanceNode).join('');
    } else {
        el.inheritanceTreeContainer.innerHTML = '<div class="empty-state">No inheritance hierarchies detected.</div>';
    }

    // 3. Cache and render Class catalog list
    cachedClassDetails = result.classDetails || [];
    renderClassCatalog(cachedClassDetails);
}

function renderInheritanceNode(node) {
    if (!node) return '';
    const hasChildren = node.subClasses && node.subClasses.length > 0;
    const shortName = node.className.split('.').pop();
    
    let html = `<div class="tree-node">
        <span class="node-label" title="${escapeHtml(node.className)}">
            <i data-lucide="git-merge"></i>
            <strong>${escapeHtml(shortName)}</strong>
            ${node.superClass ? `<span style="color: var(--text-muted); font-size:11px;"> extends ${escapeHtml(node.superClass)}</span>` : ''}
        </span>`;
        
    if (hasChildren) {
        html += node.subClasses.map(subClass => {
            // Find subclass in our mapped nodes list to render nested
            return renderInheritanceNode({ className: subClass, subClasses: [], superClass: shortName });
        }).join('');
    }
    
    html += `</div>`;
    return html;
}

function renderClassCatalog(classes) {
    if (classes.length === 0) {
        el.classesContainer.innerHTML = '<div class="empty-state">No class matching filter query.</div>';
        return;
    }

    el.classesContainer.innerHTML = classes.map((c, i) => `
        <div class="class-item" id="class-item-${i}">
            <div class="class-summary" onclick="toggleClassAccordion(${i})">
                <div class="class-name-row">
                    <span class="class-kind-badge class-${(c.type || 'class').toLowerCase()}">${c.type || 'class'}</span>
                    <span class="class-title-text">${escapeHtml(c.name)}</span>
                    <span class="class-subtitle-text">${escapeHtml(c.fullyQualifiedName)}</span>
                </div>
                <div class="class-metrics-badges">
                    <span class="metric-pill">LOC: <strong>${c.linesOfCode || 0}</strong></span>
                    <span class="metric-pill">Methods: <strong>${c.methods ? c.methods.length : 0}</strong></span>
                </div>
            </div>
            
            <div class="class-details-body hidden" id="class-body-${i}">
                <div class="details-grid">
                    <!-- Fields block -->
                    <div class="details-column">
                        <h4>Field Declarations</h4>
                        ${c.fields && c.fields.length > 0 ? `
                            <ul>
                                ${c.fields.map(f => `
                                    <li>
                                        <span>${escapeHtml(f.name)}</span>
                                        <span style="color: var(--text-muted); font-family: monospace;">${escapeHtml(f.type)}</span>
                                    </li>
                                `).join('')}
                            </ul>
                        ` : '<span style="color: var(--text-muted); font-style: italic; font-size:12px;">No fields declared</span>'}
                    </div>
                    
                    <!-- Methods list & complexity ratings -->
                    <div class="details-column">
                        <h4>Method Signatures & Complexity</h4>
                        ${c.methods && c.methods.length > 0 ? `
                            <ul>
                                ${c.methods.map(m => {
                                    const compClass = m.cyclomaticComplexity > 6 ? 'complexity-high' :
                                                      m.cyclomaticComplexity > 3 ? 'complexity-medium' : 'complexity-low';
                                    return `
                                        <li>
                                            <div>
                                                <strong>${escapeHtml(m.name)}</strong>
                                                <span style="color: var(--text-muted); font-size:11px; display:block;">
                                                    (${escapeHtml((m.parameters || []).join(', '))}) &rarr; ${escapeHtml(m.returnType)}
                                                </span>
                                            </div>
                                            <div style="text-align: right;">
                                                <span class="complexity-indicator ${compClass}">Complexity: ${m.cyclomaticComplexity || 1}</span>
                                                <span style="color: var(--text-muted); font-size:11px; display:block;">LOC: ${m.linesOfCode || 0}</span>
                                            </div>
                                        </li>
                                    `;
                                }).join('')}
                            </ul>
                        ` : '<span style="color: var(--text-muted); font-style: italic; font-size:12px;">No methods declared</span>'}
                    </div>
                </div>
            </div>
        </div>
    `).join('');
    lucide.createIcons();
}

window.toggleClassAccordion = function(index) {
    const body = document.getElementById(`class-body-${index}`);
    if (body.classList.contains('hidden')) {
        body.classList.remove('hidden');
    } else {
        body.classList.add('hidden');
    }
};

function filterClassCatalog() {
    const query = el.classSearchInput.value.toLowerCase().trim();
    if (query === '') {
        renderClassCatalog(cachedClassDetails);
        return;
    }
    
    const filtered = cachedClassDetails.filter(c => 
        c.name.toLowerCase().includes(query) || 
        c.fullyQualifiedName.toLowerCase().includes(query)
    );
    renderClassCatalog(filtered);
}

// ----------------------------------------
// API DOCUMENTATION RENDERERS
// ----------------------------------------
function renderApiDocumentationResult(result) {
    // 1. Render Endpoints List
    if (result.endpoints && result.endpoints.length > 0) {
        el.endpointsListContainer.innerHTML = result.endpoints.map((ep, i) => `
            <div class="endpoint-row" onclick="viewEndpointSpec('${ep.path}', '${ep.httpMethod.toLowerCase()}')">
                <span class="method-tag method-${ep.httpMethod.toLowerCase()}">${ep.httpMethod}</span>
                <span class="endpoint-path">${escapeHtml(ep.path)}</span>
                <span class="endpoint-controller">${escapeHtml(ep.controllerName)}</span>
            </div>
        `).join('');
    } else {
        el.endpointsListContainer.innerHTML = '<div class="empty-state">No REST Controller endpoints found.</div>';
    }

    // 2. Render Raw OpenAPI JSON Spec Document
    if (result.openapiDoc) {
        el.openapiRawCode.innerText = JSON.stringify(result.openapiDoc, null, 2);
    } else {
        el.openapiRawCode.innerText = 'No OpenAPI document generated.';
    }
}

window.viewEndpointSpec = function(path, method) {
    // Scroll raw spec view to match clicked route or highlight it
    try {
        const spec = JSON.parse(el.openapiRawCode.innerText);
        if (spec.paths && spec.paths[path] && spec.paths[path][method]) {
            const singleRouteSpec = {
                [path]: {
                    [method]: spec.paths[path][method]
                }
            };
            // Temporarily replace with highlighted endpoint block, showing toast
            showToast(`Inspecting route details for ${path}`, 'info');
        }
    } catch (e) {}
};

// ----------------------------------------
// CORE API UTILS
// ----------------------------------------
async function apiFetch(endpoint, method = 'GET', body = null) {
    const headers = {
        'Content-Type': 'application/json'
    };
    if (state.token) {
        headers['Authorization'] = `Bearer ${state.token}`;
    }

    const config = { method, headers };
    if (body) {
        config.body = JSON.stringify(body);
    }

    const res = await fetch(`${BASE_URL}${endpoint}`, config);
    if ((res.status === 401 || res.status === 403) && !endpoint.startsWith('/auth/')) {
        // Clear expired tokens
        localStorage.removeItem('jwt_token');
        state.token = null;
        showAuthOverlay(true);
        throw new Error("Unauthorized");
    }

    return await res.json();
}

// ----------------------------------------
// DATABASE ANALYZER RENDERERS
// ----------------------------------------
function renderDatabaseResult(result) {
    if (!result) return;

    el.dbTablesCount.innerText = result.entitiesCount || 0;
    el.dbRelationsCount.innerText = result.relationshipsCount || 0;

    // Cache ER diagram code in state
    state.erDiagramMermaid = result.erDiagramMermaid || null;

    if (state.currentView === 'database') {
        drawDatabaseErDiagram();
    } else {
        el.erDiagramContainer.innerHTML = '<div class="empty-state">Trigger database scan to view schema diagram.</div>';
    }

    if (result.entities && result.entities.length > 0) {
        el.dbSummaryContainer.innerHTML = result.entities.map((ent, i) => `
            <div class="class-card glass" style="margin-bottom: 1rem; padding: 1rem; border-radius: 8px; width: 100%;">
                <div style="display:flex; justify-content:space-between; align-items:center; cursor:pointer;" onclick="toggleClassAccordion('db-${i}')">
                    <div>
                        <h4 style="margin:0; font-size:15px; color:var(--accent);">Table: ${escapeHtml(ent.tableName)}</h4>
                        <span style="font-size:11px; color:var(--text-muted);">Class: ${escapeHtml(ent.className)}</span>
                    </div>
                    <span style="font-size:11px; color:var(--text-muted); border: 1px solid var(--border-color); padding: 2px 6px; border-radius: 4px;">
                        ${ent.columns.length} columns
                    </span>
                </div>
                
                <div id="class-body-db-${i}" class="margin-top" style="border-top: 1px solid var(--border-color); padding-top: 0.5rem;">
                    <table style="width:100%; border-collapse:collapse; font-size:12px; margin-top: 8px;">
                        <thead>
                            <tr style="border-bottom:1px solid var(--border-color); text-align:left; color:var(--text-muted);">
                                <th style="padding:4px;">Column</th>
                                <th style="padding:4px;">Type</th>
                                <th style="padding:4px;">Key</th>
                                <th style="padding:4px;">Null</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${ent.columns.map(col => {
                                const keyTag = col.primaryKey ? '<span style="color:#10b981; font-weight:bold;">PK</span>' : 
                                               col.foreignKey ? `<span style="color:#3b82f6; font-weight:bold;" title="FK -> ${col.referencedTable}">FK</span>` : '-';
                                return `
                                    <tr style="border-bottom:1px solid rgba(255,255,255,0.05);">
                                        <td style="padding:4px; font-family:var(--font-mono); color:var(--text-color);"><b>${escapeHtml(col.columnName)}</b></td>
                                        <td style="padding:4px; color:var(--text-muted);">${escapeHtml(col.dataType)}</td>
                                        <td style="padding:4px;">${keyTag}</td>
                                        <td style="padding:4px; color:var(--text-muted);">${col.nullable ? 'YES' : 'NO'}</td>
                                    </tr>
                                `;
                            }).join('')}
                        </tbody>
                    </table>
                </div>
            </div>
        `).join('');
    } else if (result.databaseSummary) {
        el.dbSummaryContainer.innerText = result.databaseSummary;
    } else {
        el.dbSummaryContainer.innerHTML = '<div class="empty-state">No schema catalog parsed.</div>';
    }
}

window.drawDatabaseErDiagram = function() {
    if (!state.erDiagramMermaid) {
        el.erDiagramContainer.innerHTML = '<div class="empty-state">No database entities to diagram.</div>';
        return;
    }

    const id = "er-diag-" + Date.now();
    el.erDiagramContainer.innerHTML = `<div class="mermaid" id="${id}">${state.erDiagramMermaid}</div>`;
    try {
        mermaid.initialize({ startOnLoad: false, theme: 'dark', securityLevel: 'loose' });
        mermaid.init(undefined, `#${id}`);

        setTimeout(() => {
            const svg = el.erDiagramContainer.querySelector('svg');
            if (svg) {
                svg.style.maxWidth = 'none';
                svg.style.width = 'auto';
                svg.style.height = 'auto';
                svg.style.minHeight = '450px';
            }
            // Same fit-to-screen fix as the architecture diagrams, below.
            window.fitDiagramToScreen('db');
        }, 80);
    } catch (e) {
        console.error("Mermaid rendering failed", e);
        el.erDiagramContainer.innerHTML = '<div class="empty-state" style="color:var(--danger);">Rendering failed</div>';
    }
}

// ----------------------------------------
// DIAGRAMS ROUTERS & SUB-TAB CONTROLS
// ----------------------------------------
window.switchArchSubTab = function(tabName) {
    const panelMapping = document.getElementById('archMappingsPanel');
    const panelDiagram = document.getElementById('archDiagramsPanel');
    const btnMapping = document.getElementById('tabMappingBtn');
    const btnDiagram = document.getElementById('tabDiagramBtn');

    if (tabName === 'mappings') {
        panelMapping.classList.remove('hidden');
        panelDiagram.classList.add('hidden');
        btnMapping.classList.add('active');
        btnDiagram.classList.remove('active');
        btnMapping.style.color = 'var(--text-color)';
        btnDiagram.style.color = 'var(--text-muted)';
    } else {
        panelMapping.classList.add('hidden');
        panelDiagram.classList.remove('hidden');
        btnMapping.classList.remove('active');
        btnDiagram.classList.add('active');
        btnMapping.style.color = 'var(--text-muted)';
        btnDiagram.style.color = 'var(--text-color)';
        renderSelectedMermaid();
    }
}

window.showSelectedMermaid = function(diagType) {
    state.currentDiagramType = diagType;
    document.querySelectorAll('.diag-sel-btn').forEach(btn => btn.classList.remove('active'));
    document.getElementById(`diag-btn-${diagType}`).classList.add('active');
    renderSelectedMermaid();
}

function renderSelectedMermaid() {
    const canvas = document.getElementById('mermaidRenderCanvas');
    const title = document.getElementById('mermaidDiagramTitle');
    const desc = document.getElementById('mermaidDiagramDesc');

    const meta = {
        class: { title: 'Class Diagram', desc: 'Classes mapping, fields, and inheritance tree relationships', field: 'classDiagram' },
        package: { title: 'Package Diagram', desc: 'Directories and package folder structures', field: 'packageDiagram' },
        component: { title: 'Component Diagram', desc: 'MVC controller-service-repository dependencies flow', field: 'componentDiagram' },
        sequence: { title: 'Sequence Diagram', desc: 'Scanned call flows / API execution order', field: 'sequenceDiagram' },
        usecase: { title: 'Use Case Diagram', desc: 'Scanned actor-action interaction specifications', field: 'useCaseDiagram' },
        architecture: { title: 'Architecture Diagram', desc: 'Deployment layer layout', field: 'architectureDiagram' }
    };

    const info = meta[state.currentDiagramType];
    title.innerText = info.title;
    desc.innerText = info.desc;

    if (!state.diagrams) {
        canvas.innerHTML = '<div class="empty-state">No diagrams compiled for this scan record.</div>';
        return;
    }

    const mermaidCode = state.diagrams[info.field];
    if (mermaidCode) {
        const id = "mermaid-diag-" + Date.now();
        canvas.innerHTML = `<div class="mermaid" id="${id}">${mermaidCode}</div>`;
        try {
            mermaid.initialize({ startOnLoad: false, theme: 'dark', securityLevel: 'loose' });
            mermaid.init(undefined, `#${id}`);

            setTimeout(() => {
                const svg = canvas.querySelector('svg');
                if (svg) {
                    svg.style.maxWidth = 'none';
                    svg.style.width = 'auto';
                    svg.style.height = 'auto';
                    svg.style.minHeight = '450px';
                }
                // Was: resetDiagramZoom('arch') - hardcoded flat 100% zoom, which is
                // what caused large diagrams to overflow the panel on every render.
                // fitDiagramToScreen measures the real diagram size vs. the panel's
                // real size and scales it to fit cleanly, every time.
                window.fitDiagramToScreen('arch');
            }, 80);
        } catch (e) {
            console.error("Mermaid diagram rendering failed", e);
            canvas.innerHTML = `<div class="empty-state" style="color:var(--danger);">Rendering failed: invalid diagram structure</div>`;
        }
    } else {
        canvas.innerHTML = '<div class="empty-state">Diagram details empty.</div>';
    }
}

// ----------------------------------------
// CODE QUALITY RENDERERS
// ----------------------------------------
function renderCodeQualityResult(result) {
    if (!result) return;

    const score = result.qualityScore || 100;
    el.qualityScoreText.innerText = score;

    const offset = 213.6 - (213.6 * score) / 100;
    el.qualityScoreCircle.style.strokeDashoffset = offset;

    let grade = "Excellent";
    let color = "#10b981";
    if (score < 50) {
        grade = "Needs Refactoring";
        color = "#ef4444";
    } else if (score < 80) {
        grade = "Acceptable (Warnings)";
        color = "#f59e0b";
    }
    el.qualityGradeText.innerText = grade;
    el.qualityScoreCircle.style.stroke = color;

    let critical = 0;
    let warning = 0;
    let info = 0;

    if (result.issues) {
        result.issues.forEach(i => {
            if (i.severity.toUpperCase() === "CRITICAL") critical++;
            else if (i.severity.toUpperCase() === "WARNING") warning++;
            else info++;
        });
    }

    el.qualityCriticalCount.innerText = critical;
    el.qualityWarningCount.innerText = warning;
    el.qualityInfoCount.innerText = info;

    if (result.issues && result.issues.length > 0) {
        el.qualityIssuesContainer.innerHTML = result.issues.map((issue, idx) => {
            const sevClass = issue.severity.toLowerCase();
            return `
                <div class="endpoint-card glass" style="display:flex; flex-direction:column; padding:1.25rem; gap:0.5rem; border-left:4px solid var(--${sevClass === 'critical' ? 'danger' : sevClass === 'warning' ? 'warning' : 'info'}); width: 100%;">
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="font-weight:600; color:var(--accent); font-size:14px;">${escapeHtml(issue.ruleName)}</span>
                        <span class="badge badge-${sevClass}">${escapeHtml(issue.severity)}</span>
                    </div>
                    <span style="font-size:12px; color:var(--text-muted);">
                        File: <b>${escapeHtml(issue.filePath)}</b> (Line ${issue.line})
                    </span>
                    <p style="margin:4px 0; font-size:13px; color:var(--text-color);">${escapeHtml(issue.description)}</p>
                    ${issue.snippet ? `
                        <pre style="background:rgba(0,0,0,0.3); border-radius:6px; padding:0.5rem; overflow-x:auto; margin-top:4px;"><code style="font-family:monospace; font-size:11px; color:#a7f3d0; white-space:pre;">${escapeHtml(issue.snippet)}</code></pre>
                    ` : ''}
                </div>
            `;
        }).join('');
    } else {
        el.qualityIssuesContainer.innerHTML = '<div class="empty-state">No issues detected! Your codebase conforms to all high quality clean-code specifications.</div>';
    }
}

// ----------------------------------------
// SECURITY ANALYSIS RENDERERS
// ----------------------------------------
function renderSecurityResult(result) {
    if (!result) return;

    const count = result.vulnerabilitiesCount || 0;
    el.securityScoreText.innerText = count === 1 ? '1 Vulnerability' : `${count} Vulnerabilities`;

    let critical = 0;
    let high = 0;
    let mediumOrLow = 0;

    if (result.vulnerabilities) {
        result.vulnerabilities.forEach(v => {
            const sev = v.severity.toUpperCase();
            if (sev === "CRITICAL") critical++;
            else if (sev === "HIGH") high++;
            else mediumOrLow++;
        });
    }

    el.securityCriticalCount.innerText = critical;
    el.securityHighCount.innerText = high;
    el.securityMediumCount.innerText = mediumOrLow;

    if (result.vulnerabilities && result.vulnerabilities.length > 0) {
        el.securityIssuesContainer.innerHTML = result.vulnerabilities.map(v => {
            const sevClass = v.severity.toLowerCase();
            return `
                <div class="endpoint-card glass" style="display:flex; flex-direction:column; padding:1.25rem; gap:0.5rem; border-left:4px solid var(--${sevClass === 'critical' || sevClass === 'high' ? 'danger' : 'info'}); width: 100%;">
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="font-weight:600; color:var(--accent); font-size:14px;">${escapeHtml(v.vulnType)}</span>
                        <span class="badge badge-${sevClass === 'critical' ? 'critical' : sevClass === 'high' ? 'danger' : 'warning'}">${escapeHtml(v.severity)}</span>
                    </div>
                    <span style="font-size:12px; color:var(--text-muted);">
                        File: <b>${escapeHtml(v.filePath)}</b> (Line ${v.line})
                    </span>
                    <p style="margin:4px 0; font-size:13px; color:var(--text-color);">${escapeHtml(v.description)}</p>
                    <div style="background:rgba(255,100,100,0.05); border:1px solid rgba(255,100,100,0.1); border-radius:6px; padding:0.5rem; margin-top:4px;">
                        <span style="font-size:11px; font-weight:600; color:#f87171;">Remediation Fix:</span>
                        <p style="margin:2px 0 0 0; font-size:12px; color:var(--text-muted);">${escapeHtml(v.resolution)}</p>
                    </div>
                    ${v.snippet ? `
                        <pre style="background:rgba(0,0,0,0.3); border-radius:6px; padding:0.5rem; overflow-x:auto; margin-top:4px;"><code style="font-family:monospace; font-size:11px; color:#fca5a5; white-space:pre;">${escapeHtml(v.snippet)}</code></pre>
                    ` : ''}
                </div>
            `;
        }).join('');
    } else {
        el.securityIssuesContainer.innerHTML = '<div class="empty-state">No security vulnerabilities detected! Your codebase conforms to secure coding practices.</div>';
    }
}

// ----------------------------------------
// SPECIFICATION & REPORTS EXPORT ROUTINES
// ----------------------------------------
window.exportReport = function(format) {
    if (!state.activeRepoId) {
        showToast("Please select a repository first.", 'error');
        return;
    }

    const activeRepo = state.repos.find(r => r.id === state.activeRepoId);
    const repoName = activeRepo ? activeRepo.repoName : "project";

    if (format === 'openapi') {
        const apiData = state.analysisCache['API_DOCUMENTATION'];
        if (!apiData || !apiData.openapiDoc) {
            showToast("OpenAPI spec not compiled yet. Please run API scan first.", 'warning');
            return;
        }
        downloadFile(`${repoName}_openapi.json`, JSON.stringify(apiData.openapiDoc, null, 2), "application/json");
        return;
    }

    if (format === 'quality') {
        const qualityData = state.analysisCache['CODE_QUALITY'];
        if (!qualityData || !qualityData.issues) {
            showToast("Quality spec not compiled yet. Please run Code Quality scan first.", 'warning');
            return;
        }
        downloadFile(`${repoName}_quality.json`, JSON.stringify(qualityData.issues, null, 2), "application/json");
        return;
    }

    if (format === 'svg') {
        const svgEl = document.querySelector("#graphCanvas svg");
        if (!svgEl) {
            showToast("No active architecture layout rendered on screen.", 'warning');
            return;
        }
        const serializer = new XMLSerializer();
        let source = serializer.serializeToString(svgEl);
        if(!source.startsWith('<?xml')){
            source = '<?xml version="1.0" standalone="no"?>\r\n' + source;
        }
        downloadFile(`${repoName}_architecture.svg`, source, "image/svg+xml");
        return;
    }

    if (format === 'markdown') {
        const markdownContent = compileMarkdownReport(repoName);
        downloadFile(`${repoName}_specification_report.md`, markdownContent, "text/markdown");
        return;
    }

    if (format === 'html') {
        const markdownContent = compileMarkdownReport(repoName);
        const htmlContent = `<!DOCTYPE html>
<html>
<head>
    <title>${repoName} — Platform Specifications Report</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #e2e8f0; background: #0f0f17; padding: 2rem; max-width: 900px; margin: 0 auto; }
        h1, h2, h3 { color: #818cf8; border-bottom: 1px solid #2d2d3d; padding-bottom: 0.5rem; margin-top: 2rem; }
        pre { background: #1e1e2e; padding: 1rem; border-radius: 8px; border: 1px solid #2d2d3d; overflow-x: auto; color: #a7f3d0; }
        code { font-family: monospace; }
        ul { padding-left: 1.5rem; }
        li { margin-bottom: 0.5rem; }
        table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
        th, td { border: 1px solid #2d2d3d; padding: 10px; text-align: left; }
        th { background: #1e1e2e; color: #818cf8; }
        .meta-box { background: rgba(99, 102, 241, 0.1); border: 1px solid rgba(99, 102, 241, 0.2); border-radius: 8px; padding: 1rem; margin-bottom: 2rem; }
    </style>
</head>
<body>
    <div class="meta-box">
        <h1 style="margin-top:0; border-bottom:none;">${repoName}</h1>
        <p>Generated by <strong>CodeNavigator AI</strong> on ${new Date().toLocaleDateString()}</p>
    </div>
    ${convertSimpleMarkdownToHtml(markdownContent)}
</body>
</html>`;
        downloadFile(`${repoName}_specification_report.html`, htmlContent, "text/html");
        return;
    }
}

function downloadFile(filename, content, mimeType) {
    const blob = new Blob([content], { type: mimeType });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

function compileMarkdownReport(repoName) {
    const arch = state.analysisCache['ARCHITECTURE'];
    const metrics = state.analysisCache['SOURCE_CODE'];
    const apidocs = state.analysisCache['API_DOCUMENTATION'];
    const db = state.analysisCache['DATABASE'];
    const quality = state.analysisCache['CODE_QUALITY'];
    const security = state.analysisCache['SECURITY'];

    let md = "# CodeNavigator AI Platform Specification Report: " + repoName + "\\n\\n";
    md += "Generated on: " + new Date().toLocaleString() + "\\n\\n";

    md += "## 1. Project Summary\\n\\n";
    if (metrics) {
        md += "* **Framework**: " + (metrics.framework || "Undetected") + "\\n";
        md += "* **Build Tool**: " + (metrics.buildTool || "Undetected") + "\\n";
        md += "* **Primary Language**: " + (metrics.primaryLanguage || "Java") + "\\n";
        md += "* **Total Class Files**: " + (metrics.classesCount || 0) + "\\n";
        md += "* **Total Method Scopes**: " + (metrics.methodsCount || 0) + "\\n";
        md += "* **Lines of Code**: " + (metrics.linesOfCode || 0) + "\\n";
        md += "* **Average Cyclomatic Complexity**: " + (metrics.avgComplexity || 0) + "\\n\\n";
    } else {
        md += "*Scan source code to populate project summaries.*\\n\\n";
    }

    md += "## 2. Package Architecture\\n\\n";
    if (arch && arch.diagrams) {
        md += "### Class Diagram\\n\\n";
        md += "\`\`\`mermaid\\n" + (arch.diagrams.classDiagram || "") + "\\n\`\`\`\\n\\n";
        md += "### Component Mappings\\n\\n";
        md += "\`\`\`mermaid\\n" + (arch.diagrams.componentDiagram || "") + "\\n\`\`\`\\n\\n";
    } else {
        md += "*Scan architecture packages to populate diagrams.*\\n\\n";
    }

    md += "## 3. API Specifications\\n\\n";
    if (apidocs && apidocs.endpoints) {
        md += "| Method | Endpoint | Description | Return Type |\\n";
        md += "|--------|----------|-------------|-------------|\\n";
        apidocs.endpoints.forEach(e => {
            md += "| " + e.httpMethod + " | " + e.url + " | " + (e.description || "API route") + " | " + (e.returnType || "void") + " |\\n";
        });
        md += "\\n";
    } else {
        md += "*Scan api documentation to populate endpoints catalog.*\\n\\n";
    }

    md += "## 4. Database Schema Catalog\\n\\n";
    if (db && db.entities) {
        db.entities.forEach(ent => {
            md += "### Table: " + ent.tableName + " (Class: " + ent.className + ")\\n\\n";
            md += "| Column | Data Type | Key Type | Nullable |\\n";
            md += "|--------|-----------|----------|----------|\\n";
            ent.columns.forEach(col => {
                const key = col.primaryKey ? "PK" : col.foreignKey ? "FK" : "-";
                md += "| " + col.columnName + " | " + col.dataType + " | " + key + " | " + (col.nullable ? "YES" : "NO") + " |\\n";
            });
            md += "\\n";
        });
    } else {
        md += "*Scan database entities to populate schema catalogs.*\\n\\n";
    }

    md += "## 5. Code Quality Report\\n\\n";
    if (quality) {
        md += "**Platform Quality Score**: **" + quality.qualityScore + " / 100**\\n\\n";
        md += "### Identified Smells\\n\\n";
        if (quality.issues && quality.issues.length > 0) {
            quality.issues.forEach(i => {
                md += "* **[" + i.severity + "] " + i.ruleName + "** in \`" + i.filePath + "\` (Line " + i.line + ")\\n";
                md += "  *Description*: " + i.description + "\\n\\n";
            });
        } else {
            md += "*No code quality smells detected.*\\n\\n";
        }
    } else {
        md += "*Scan code quality to compile scores.*\\n\\n";
    }

    md += "## 6. Security Vulnerability Catalog\\n\\n";
    if (security) {
        md += "**Total Vulnerabilities**: **" + security.vulnerabilitiesCount + "**\\n\\n";
        if (security.vulnerabilities && security.vulnerabilities.length > 0) {
            security.vulnerabilities.forEach(v => {
                md += "### *[" + v.severity + "] " + v.vulnType + "*\\n";
                md += "* **File**: \`" + v.filePath + "\` (Line " + v.line + ")\\n";
                md += "* **Description**: " + v.description + "\\n";
                md += "* **Remediation**: " + v.resolution + "\\n\\n";
            });
        } else {
            md += "*No security vulnerabilities detected.*\\n\\n";
        }
    } else {
        md += "*Scan security risks to compile catalogs.*\\n\\n";
    }

    return md;
}

function convertSimpleMarkdownToHtml(md) {
    return md
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/^## (.*$)/gim, '<h2>$1</h2>')
        .replace(/^### (.*$)/gim, '<h3>$1</h3>')
        .replace(/^# (.*$)/gim, '<h1>$1</h1>')
        .replace(/\`\`\`(.*?)\n([\s\S]*?)\`\`\`/gim, '<pre><code>$2</code></pre>')
        .replace(/\`([^`]+)\`/gim, '<code>$1</code>')
        .replace(/\*\*([^*]+)\*\*/gim, '<strong>$1</strong>')
        .replace(/^\* (.*$)/gim, '<li>$1</li>')
        .replace(/\n/g, '<br/>');
}






// ----------------------------------------
// USER NOTIFICATION UTILS
// ----------------------------------------
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    
    let iconName = 'info';
    if (type === 'success') iconName = 'check-circle';
    if (type === 'error') iconName = 'alert-triangle';
    
    toast.innerHTML = `
        <i data-lucide="${iconName}"></i>
        <span>${escapeHtml(message)}</span>
    `;
    
    el.toastContainer.appendChild(toast);
    lucide.createIcons();
    
    // Auto dismiss
    setTimeout(() => {
        toast.style.animation = 'slideIn 0.2s ease-out reverse';
        setTimeout(() => toast.remove(), 200);
    }, 4000);
}

// Safety sanitation helpers
function escapeHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function formatSize(kb) {
    if (kb > 1024) return (kb / 1024).toFixed(1) + ' MB';
    return kb + ' KB';
}

// ----------------------------------------
// DIAGRAM ZOOMING ACTION UTILITIES
// ----------------------------------------
// Widened from 0.4 to 0.1 so very large auto-generated diagrams (e.g. a class
// diagram for a big repo) can still fit-to-screen instead of being clamped
// to a minimum zoom that's still too large for the container.
const MIN_DIAGRAM_ZOOM = 0.1;
const MAX_DIAGRAM_ZOOM = 3.0;
const DIAGRAM_FIT_PADDING = 0.92; // leave a small visual margin instead of edge-to-edge

window.zoomDiagram = function(type, delta) {
    state.zooms[type] = Math.min(Math.max(state.zooms[type] + delta, MIN_DIAGRAM_ZOOM), MAX_DIAGRAM_ZOOM);
    applyDiagramZoom(type);
};

// "Reset" now means "back to the sensible default" (fit-to-screen), not a
// hardcoded 100% - 100% of an oversized diagram is exactly what was causing
// the out-of-layout/overflow problem in the first place.
window.resetDiagramZoom = function(type) {
    fitDiagramToScreen(type);
};

function diagramContainerFor(type) {
    return document.getElementById(type === 'arch' ? 'mermaidRenderCanvas' : 'erDiagramContainer');
}

/**
 * Measures the diagram's real rendered size (via its SVG viewBox, which
 * Mermaid always sets) against the panel's actual available space, and
 * computes the zoom level that makes the whole diagram visible at once -
 * the same idea as "zoom to fit" in any professional diagramming tool.
 */
function computeFitScale(container, svg) {
    const containerWidth = container.clientWidth - 24;  // minus inner padding breathing room
    const containerHeight = Math.max(container.clientHeight - 24, 360);

    let svgWidth, svgHeight;
    const viewBox = svg.getAttribute('viewBox');
    if (viewBox) {
        const parts = viewBox.split(/\s+/).map(Number);
        svgWidth = parts[2];
        svgHeight = parts[3];
    } else {
        const box = svg.getBBox();
        svgWidth = box.width;
        svgHeight = box.height;
    }

    if (!svgWidth || !svgHeight || !containerWidth || !containerHeight) {
        return 1.0;
    }

    const scale = Math.min(containerWidth / svgWidth, containerHeight / svgHeight) * DIAGRAM_FIT_PADDING;
    return Math.min(Math.max(scale, MIN_DIAGRAM_ZOOM), MAX_DIAGRAM_ZOOM);
}

window.fitDiagramToScreen = function(type) {
    const container = diagramContainerFor(type);
    if (!container) return;
    const svg = container.querySelector('svg');
    if (!svg) return;

    state.zooms[type] = computeFitScale(container, svg);
    applyDiagramZoom(type);
};

function applyDiagramZoom(type) {
    const containerId = type === 'arch' ? 'mermaidRenderCanvas' : 'erDiagramContainer';
    const textId = type === 'arch' ? 'archZoomText' : 'dbZoomText';

    const container = document.getElementById(containerId);
    const text = document.getElementById(textId);
    if (!container) return;

    const svg = container.querySelector('svg');
    if (svg) {
        svg.style.transform = `scale(${state.zooms[type]})`;
        svg.style.transformOrigin = 'top center';
    }

    if (text) {
        text.innerText = `${Math.round(state.zooms[type] * 100)}%`;
    }
}

// Re-fit the currently visible diagram when the window/panel is resized,
// so a browser resize never leaves a diagram stuck at a stale scale.
let diagramResizeTimer = null;
window.addEventListener('resize', () => {
    clearTimeout(diagramResizeTimer);
    diagramResizeTimer = setTimeout(() => {
        const archPanel = document.getElementById('archDiagramsPanel');
        if (archPanel && !archPanel.classList.contains('hidden')) {
            window.fitDiagramToScreen('arch');
        }
        if (el.erDiagramContainer && el.erDiagramContainer.querySelector('svg')) {
            window.fitDiagramToScreen('db');
        }
    }, 200);
});

// ----------------------------------------
// AI CHAT ASSISTANT ACTION UTILITIES
// ----------------------------------------
window.handleChatKey = function(e) {
    if (e.key === 'Enter') {
        submitChatPrompt();
    }
};

window.submitQuickChat = function(text) {
    const chatInput = document.getElementById('chatInputPrompt');
    if (chatInput) {
        chatInput.value = text;
        submitChatPrompt();
    }
};

window.submitChatPrompt = async function() {
    const inputEl = document.getElementById('chatInputPrompt');
    const logEl = document.getElementById('chatMessageLog');
    if (!inputEl || !inputEl.value.trim() || !logEl) return;

    if (!state.activeRepoId) {
        showToast("Please select an active repository first.", 'error');
        return;
    }

    const promptText = inputEl.value.trim();
    inputEl.value = '';

    // Append User Message
    appendChatMessage(logEl, 'User', promptText, 'user');

    // Append Typing Indicator
    const typingIndicator = appendTypingIndicator(logEl);

    try {
        const payload = {
            repositoryId: state.activeRepoId,
            prompt: promptText
        };
        const res = await apiFetch('/analyses/chat', 'POST', payload);
        typingIndicator.remove();

        if (res.success) {
            const aiMsg = res.data.message;
            const mode = res.data.mode;
            const author = `AI Navigator (${mode})`;
            appendChatMessage(logEl, author, aiMsg, 'ai');
        } else {
            appendChatMessage(logEl, 'System', "Error compiling response.", 'system');
        }
    } catch (e) {
        typingIndicator.remove();
        appendChatMessage(logEl, 'System', "Failed to connect to AI assistant.", 'system');
    }
};

function appendChatMessage(container, author, text, type) {
    const bubble = document.createElement('div');
    bubble.className = `chat-bubble ${type}`;
    
    // Markdown-like simple HTML formatting for code blocks and links
    let formattedText = escapeHtml(text)
        .replace(/\n/g, '<br/>')
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.*?)\*/g, '<em>$1</em>')
        .replace(/\`([^`]+)\`/g, '<code style="background:rgba(0,0,0,0.3); padding:2px 6px; border-radius:4px; font-family:monospace; color:#818cf8;">$1</code>');

    bubble.innerHTML = `
        <span class="chat-author">${author}</span>
        <p class="chat-text">${formattedText}</p>
    `;
    container.appendChild(bubble);
    container.scrollTop = container.scrollHeight;
}

function appendTypingIndicator(container) {
    const bubble = document.createElement('div');
    bubble.className = 'chat-bubble ai';
    bubble.innerHTML = `
        <span class="chat-author">AI Navigator</span>
        <div style="display:flex; gap:4px; margin-top:8px;" class="typing-indicator">
            <span style="width:8px; height:8px; background:var(--primary); border-radius:50%; display:inline-block; animation: typing-bounce 1s infinite alternate;"></span>
            <span style="width:8px; height:8px; background:var(--primary); border-radius:50%; display:inline-block; animation: typing-bounce 1s infinite alternate; animation-delay:0.2s;"></span>
            <span style="width:8px; height:8px; background:var(--primary); border-radius:50%; display:inline-block; animation: typing-bounce 1s infinite alternate; animation-delay:0.4s;"></span>
        </div>
    `;
    container.appendChild(bubble);
    container.scrollTop = container.scrollHeight;
    return bubble;
}
