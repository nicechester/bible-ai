/**
 * Main application module for Bible-AI frontend.
 * This module initializes the React app with all components.
 * 
 * Note: Since we're using React from CDN with Babel standalone,
 * this file creates the components and renders them.
 */

import { sendQuery, getConfig } from './utils/api.js';
import { linkifyVerseReferences } from './utils/verseParser.js';
import { getFullBookName } from './utils/bookMappings.js';

// Generate session ID
function generateSessionId() {
    return 'session-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
}

// Initialize the app when React is ready
export function initApp(React, ReactDOM) {
    const { useState, useEffect, useRef, useCallback } = React;
    
    // MarkdownRenderer component
    function MarkdownRenderer({ content, onVerseClick }) {
        const [html, setHtml] = useState('');
        const containerRef = useRef(null);
        const mermaidInitialized = useRef(false);
        
        useEffect(() => {
            if (content && typeof marked !== 'undefined') {
                try {
                    marked.setOptions({ breaks: true, gfm: true });
                    
                    const renderer = new marked.Renderer();
                    const originalCode = renderer.code.bind(renderer);
                    
                    renderer.code = function(code, language) {
                        if (language === 'mermaid') {
                            return `<div class="mermaid">${code}</div>`;
                        }
                        return originalCode(code, language);
                    };
                    
                    let rendered = marked.parse(content, { renderer });
                    
                    rendered = rendered.replace(
                        /<pre><code\s+class="language-mermaid">([\s\S]*?)<\/code><\/pre>/gi,
                        (match, code) => {
                            const textarea = document.createElement('textarea');
                            textarea.innerHTML = code;
                            return `<div class="mermaid">${textarea.value.trim()}</div>`;
                        }
                    );
                    
                    rendered = linkifyVerseReferences(rendered);
                    setHtml(rendered);
                } catch (e) {
                    console.error('Markdown rendering error:', e);
                    setHtml(content);
                }
            } else {
                setHtml(content || '');
            }
        }, [content]);
        
        useEffect(() => {
            if (html && typeof mermaid !== 'undefined' && containerRef.current) {
                const renderMermaid = async () => {
                    try {
                        if (!mermaidInitialized.current) {
                            mermaid.initialize({ startOnLoad: true, theme: 'default', securityLevel: 'loose' });
                            mermaidInitialized.current = true;
                        }
                        
                        containerRef.current.querySelectorAll('.mermaid').forEach(el => {
                            el.removeAttribute('data-processed');
                        });
                        
                        if (typeof mermaid.run === 'function') {
                            await mermaid.run({ querySelector: '.mermaid', suppressErrors: true });
                        } else if (typeof mermaid.contentLoaded === 'function') {
                            mermaid.contentLoaded();
                        }
                    } catch (err) {
                        console.error('Mermaid error:', err);
                    }
                };
                setTimeout(renderMermaid, 100);
            }
        }, [html]);
        
        useEffect(() => {
            if (!containerRef.current || !onVerseClick) return;
            
            const handleClick = (e) => {
                const link = e.target.closest('.verse-link');
                if (link) {
                    e.preventDefault();
                    const isChapterOnly = link.dataset.chapterOnly === 'true';
                    onVerseClick(
                        link.dataset.book,
                        parseInt(link.dataset.chapter),
                        parseInt(link.dataset.start),
                        link.dataset.end ? parseInt(link.dataset.end) : null,
                        isChapterOnly
                    );
                }
            };
            
            containerRef.current.addEventListener('click', handleClick);
            return () => containerRef.current?.removeEventListener('click', handleClick);
        }, [html, onVerseClick]);
        
        return React.createElement('div', { ref: containerRef, dangerouslySetInnerHTML: { __html: html } });
    }
    
    // VersePanel component
    function VersePanel({ book, chapter, verse, endVerse, onClose, onReadChapter }) {
        const [data, setData] = useState(null);
        const [loading, setLoading] = useState(true);
        const [currentVerse, setCurrentVerse] = useState(verse);
        
        const loadData = useCallback(async () => {
            if (!book || !chapter || !currentVerse) return;
            setLoading(true);
            try {
                const response = await fetch(`/api/bible/${book}/${chapter}/${currentVerse}/context?size=2`);
                if (response.ok) setData(await response.json());
            } catch (err) {
                console.error('Failed to load verse:', err);
            }
            setLoading(false);
        }, [book, chapter, currentVerse]);
        
        useEffect(() => { loadData(); }, [loadData]);
        useEffect(() => { setCurrentVerse(verse); }, [verse]);
        
        useEffect(() => {
            const handleKey = (e) => {
                if (e.key === 'Escape') onClose();
                else if (e.key === 'ArrowLeft' && currentVerse > 1) setCurrentVerse(v => v - 1);
                else if (e.key === 'ArrowRight') setCurrentVerse(v => v + 1);
            };
            window.addEventListener('keydown', handleKey);
            return () => window.removeEventListener('keydown', handleKey);
        }, [currentVerse, onClose]);
        
        if (!book) return null;
        
        const fullName = getFullBookName(book);
        const ref = endVerse ? `${fullName} ${chapter}:${currentVerse}-${endVerse}` : `${fullName} ${chapter}:${currentVerse}`;
        
        return React.createElement('div', { className: 'verse-preview-panel' },
            React.createElement('div', { className: 'verse-preview-header' },
                React.createElement('h4', null, ref),
                React.createElement('button', { className: 'verse-preview-close', onClick: onClose }, '×')
            ),
            React.createElement('div', { className: 'verse-preview-content' },
                loading ? React.createElement('div', { className: 'loading' }, 'Loading...') :
                data && React.createElement('div', { className: 'verse-context' },
                    data.verses.map(v => React.createElement('div', {
                        key: v.verse,
                        className: `context-verse ${v.verse >= currentVerse && v.verse <= (endVerse || currentVerse) ? 'highlighted' : ''}`
                    },
                        React.createElement('span', { className: 'verse-num' }, v.verse),
                        React.createElement('span', { className: 'verse-text' }, v.text)
                    ))
                )
            ),
            React.createElement('div', { className: 'verse-preview-footer' },
                React.createElement('button', { className: 'btn-secondary', onClick: () => currentVerse > 1 && setCurrentVerse(v => v - 1) }, '← Prev'),
                React.createElement('button', { className: 'btn-secondary', onClick: () => setCurrentVerse(v => v + 1) }, 'Next →'),
                React.createElement('button', { className: 'btn-primary', onClick: () => onReadChapter?.(book, chapter, currentVerse) }, 'Read Chapter')
            )
        );
    }
    
    // Main App component
    function App() {
        const [messages, setMessages] = useState([]);
        const [input, setInput] = useState('');
        const [loading, setLoading] = useState(false);
        const [previewData, setPreviewData] = useState(null);
        const [bibleVersion, setBibleVersion] = useState('개역개정');
        const [showGreeting, setShowGreeting] = useState(true);
        const [sessionId, setSessionId] = useState(() => generateSessionId());
        const [versePreview, setVersePreview] = useState(null);
        const [conversationWidth, setConversationWidth] = useState(30);
        const [isDragging, setIsDragging] = useState(false);
        const messagesEndRef = useRef(null);
        const inputRef = useRef(null);
        const containerRef = useRef(null);
        
        useEffect(() => {
            getConfig().then(data => setBibleVersion(data.version || '개역개정')).catch(() => {});
        }, []);
        
        const scrollToBottom = () => messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        useEffect(scrollToBottom, [messages, showGreeting]);
        
        const startNewSession = () => {
            setSessionId(generateSessionId());
            setMessages([]);
            setPreviewData(null);
            setShowGreeting(true);
            setVersePreview(null);
            inputRef.current?.focus();
        };
        
        const handleSend = async () => {
            if (!input.trim() || loading) return;
            if (showGreeting) setShowGreeting(false);
            
            const userMessage = input.trim();
            setInput('');
            setLoading(true);
            setMessages(prev => [...prev, { role: 'user', content: userMessage }]);
            
            try {
                const data = await sendQuery(userMessage, sessionId);
                setMessages(prev => [...prev, { role: 'assistant', content: data.summary || 'No response' }]);
                setPreviewData(data);
            } catch (error) {
                setMessages(prev => [...prev, { role: 'assistant', content: 'Error: ' + error.message }]);
                setPreviewData({ error: error.message });
            }
            setLoading(false);
        };
        
        const handleVerseClick = (book, chapter, verse, endVerse, isChapterOnly = false) => {
            if (isChapterOnly) {
                // For chapter-only references, open the reader directly
                window.open(`/views/reader.html?book=${book}&chapter=${chapter}`, '_blank');
            } else {
                setVersePreview({ book, chapter, verse, endVerse });
            }
        };
        
        const handleReadChapter = (book, chapter, verse) => {
            window.open(`/views/reader.html?book=${book}&chapter=${chapter}&verse=${verse}`, '_blank');
        };
        
        // Resizer handling
        const handleMouseDown = (e) => { setIsDragging(true); e.preventDefault(); };
        
        useEffect(() => {
            if (!isDragging) return;
            
            const handleMove = (e) => {
                const width = (e.clientX / containerRef.current.offsetWidth) * 100;
                setConversationWidth(Math.max(20, Math.min(70, width)));
            };
            const handleUp = () => setIsDragging(false);
            
            document.addEventListener('mousemove', handleMove);
            document.addEventListener('mouseup', handleUp);
            document.body.style.cursor = 'col-resize';
            document.body.style.userSelect = 'none';
            
            return () => {
                document.removeEventListener('mousemove', handleMove);
                document.removeEventListener('mouseup', handleUp);
                document.body.style.cursor = '';
                document.body.style.userSelect = '';
            };
        }, [isDragging]);
        
        return React.createElement('div', { id: 'root', ref: containerRef },
            // Header
            React.createElement('div', { className: 'app-header' },
                'Bible AI',
                React.createElement('span', { className: 'bible-info' }, ` • ${bibleVersion}`)
            ),
            
            // Main container
            React.createElement('div', { className: 'main-container' },
                // Conversation pane
                React.createElement('div', { className: 'conversation-pane', style: { width: `${conversationWidth}%` } },
                    React.createElement('div', { className: 'conversation-header' },
                        React.createElement('span', null, 'Conversation'),
                        React.createElement('button', { className: 'new-session-button', onClick: startNewSession, title: 'Start new session' }, '+')
                    ),
                    React.createElement('div', { className: 'messages-container' },
                        showGreeting && React.createElement('div', { className: 'greeting-message' },
                            React.createElement('h3', null, '📖 Welcome to Bible AI'),
                            React.createElement('p', null, "I'm your Bible study assistant for the Korean Revised Version (개역개정) Bible."),
                            React.createElement('p', { style: { marginTop: '8px' } }, 'Try asking:'),
                            React.createElement('ul', null,
                                React.createElement('li', null, '"창세기 1장 1절을 보여줘"'),
                                React.createElement('li', null, '"사랑에 대한 구절을 찾아줘"'),
                                React.createElement('li', null, '"백부장이 나온 구절을 그림으로 설명해줘"')
                            )
                        ),
                        messages.map((msg, idx) => React.createElement('div', { key: idx, className: `message ${msg.role}` }, msg.content)),
                        loading && React.createElement('div', { className: 'loading' }, 'Thinking...'),
                        React.createElement('div', { ref: messagesEndRef })
                    ),
                    React.createElement('div', { className: 'input-container' },
                        React.createElement('div', { className: 'input-form' },
                            React.createElement('input', {
                                ref: inputRef,
                                className: 'input-field',
                                type: 'text',
                                value: input,
                                onChange: (e) => setInput(e.target.value),
                                onKeyPress: (e) => e.key === 'Enter' && !e.shiftKey && (e.preventDefault(), handleSend()),
                                placeholder: 'Ask a question about the Bible...',
                                disabled: loading
                            }),
                            React.createElement('button', { className: 'send-button', onClick: handleSend, disabled: loading || !input.trim() }, 'Send')
                        )
                    )
                ),
                
                // Resizer
                React.createElement('div', { className: `resizer ${isDragging ? 'dragging' : ''}`, onMouseDown: handleMouseDown }),
                
                // Preview window
                React.createElement('div', { className: 'preview-window' },
                    React.createElement('div', { className: 'preview-header' }, 'Preview'),
                    React.createElement('div', { className: 'preview-content' },
                        previewData?.error && React.createElement('div', { className: 'error' }, previewData.error),
                        previewData?.summary && React.createElement('div', { className: 'summary-section' },
                            React.createElement(MarkdownRenderer, { content: previewData.summary, onVerseClick: handleVerseClick })
                        ),
                        !previewData && React.createElement('div', { className: 'loading' }, 'No preview available. Start a conversation to see results.')
                    )
                )
            ),
            
            // Verse preview panel
            versePreview && React.createElement(VersePanel, {
                book: versePreview.book,
                chapter: versePreview.chapter,
                verse: versePreview.verse,
                endVerse: versePreview.endVerse,
                onClose: () => setVersePreview(null),
                onReadChapter: handleReadChapter
            })
        );
    }
    
    // Render the app
    ReactDOM.render(React.createElement(App), document.getElementById('root'));
}

// Export for use in HTML
window.initBibleApp = initApp;
