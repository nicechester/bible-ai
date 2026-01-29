/**
 * MarkdownRenderer component for rendering markdown with Mermaid and verse links.
 * Note: This is designed to be used with React loaded from CDN.
 * Use this with Babel standalone for JSX transformation.
 */

import { linkifyVerseReferences } from '../utils/verseParser.js';

/**
 * Initialize the MarkdownRenderer as a React component.
 * Call this after React and ReactDOM are loaded.
 * 
 * Usage in JSX:
 * <MarkdownRenderer content={markdownText} onVerseClick={handleVerseClick} />
 * 
 * @param {Object} React - React library
 * @returns {Function} - React component
 */
export function createMarkdownRenderer(React) {
    const { useState, useEffect, useRef } = React;
    
    return function MarkdownRenderer({ content, onVerseClick }) {
        const [html, setHtml] = useState('');
        const containerRef = useRef(null);
        const mermaidInitialized = useRef(false);
        
        // Parse and render markdown
        useEffect(() => {
            if (content && typeof marked !== 'undefined') {
                try {
                    marked.setOptions({
                        breaks: true,
                        gfm: true
                    });
                    
                    const renderer = new marked.Renderer();
                    const originalCode = renderer.code.bind(renderer);
                    
                    renderer.code = function(code, language) {
                        if (language === 'mermaid') {
                            return `<div class="mermaid">${code}</div>`;
                        }
                        return originalCode(code, language);
                    };
                    
                    let rendered = marked.parse(content, { renderer });
                    
                    // Handle mermaid code blocks
                    rendered = rendered.replace(
                        /<pre><code\s+class="language-mermaid">([\s\S]*?)<\/code><\/pre>/gi,
                        (match, code) => {
                            const textarea = document.createElement('textarea');
                            textarea.innerHTML = code;
                            const decoded = textarea.value.trim();
                            return `<div class="mermaid">${decoded}</div>`;
                        }
                    );
                    
                    // Linkify verse references
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
        
        // Render mermaid diagrams
        useEffect(() => {
            if (html && typeof mermaid !== 'undefined' && containerRef.current) {
                const renderMermaid = async () => {
                    try {
                        if (!mermaidInitialized.current) {
                            const initConfig = { 
                                startOnLoad: true,
                                theme: 'default',
                                securityLevel: 'loose'
                            };
                            
                            if (typeof mermaid.initialize === 'function') {
                                const initResult = mermaid.initialize(initConfig);
                                if (initResult && typeof initResult.then === 'function') {
                                    await initResult;
                                }
                            }
                            mermaidInitialized.current = true;
                        }
                        
                        const allMermaidElements = containerRef.current.querySelectorAll('.mermaid');
                        allMermaidElements.forEach((element) => {
                            element.removeAttribute('data-processed');
                        });
                        
                        if (typeof mermaid.contentLoaded === 'function') {
                            mermaid.contentLoaded();
                        } else if (typeof mermaid.run === 'function') {
                            await mermaid.run({
                                querySelector: '.mermaid',
                                suppressErrors: true
                            });
                        }
                    } catch (err) {
                        console.error('Mermaid rendering error:', err);
                    }
                };
                
                const timeoutId = setTimeout(renderMermaid, 100);
                return () => clearTimeout(timeoutId);
            }
        }, [html]);
        
        // Handle verse link clicks
        useEffect(() => {
            if (containerRef.current && onVerseClick) {
                const handleClick = (e) => {
                    const link = e.target.closest('.verse-link');
                    if (link) {
                        e.preventDefault();
                        const book = link.dataset.book;
                        const chapter = parseInt(link.dataset.chapter);
                        const start = parseInt(link.dataset.start);
                        const end = link.dataset.end ? parseInt(link.dataset.end) : null;
                        onVerseClick(book, chapter, start, end);
                    }
                };
                
                containerRef.current.addEventListener('click', handleClick);
                return () => {
                    if (containerRef.current) {
                        containerRef.current.removeEventListener('click', handleClick);
                    }
                };
            }
        }, [html, onVerseClick]);
        
        return React.createElement('div', {
            ref: containerRef,
            dangerouslySetInnerHTML: { __html: html }
        });
    };
}

export default createMarkdownRenderer;
