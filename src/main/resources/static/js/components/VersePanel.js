/**
 * VersePanel component for displaying verse preview with context.
 * Note: This is designed to be used with React loaded from CDN.
 */

import { getVerseWithContext } from '../utils/api.js';
import { getFullBookName } from '../utils/bookMappings.js';
import { formatVerseReference, isVerseInRange } from '../utils/verseParser.js';

/**
 * Create the VersePanel React component.
 * @param {Object} React - React library
 * @returns {Function} - React component
 */
export function createVersePanel(React) {
    const { useState, useEffect, useCallback } = React;
    
    return function VersePanel({ book, chapter, verse, endVerse, onClose, onReadChapter }) {
        const [data, setData] = useState(null);
        const [loading, setLoading] = useState(true);
        const [error, setError] = useState(null);
        const [currentVerse, setCurrentVerse] = useState(verse);
        
        // Load verse data
        const loadVerseData = useCallback(async () => {
            if (!book || !chapter || !currentVerse) return;
            
            setLoading(true);
            setError(null);
            
            try {
                const result = await getVerseWithContext(book, chapter, currentVerse, 2);
                setData(result);
            } catch (err) {
                setError(err.message);
            } finally {
                setLoading(false);
            }
        }, [book, chapter, currentVerse]);
        
        useEffect(() => {
            loadVerseData();
        }, [loadVerseData]);
        
        // Update current verse when prop changes
        useEffect(() => {
            setCurrentVerse(verse);
        }, [verse]);
        
        // Navigation handlers
        const handlePrevVerse = () => {
            if (currentVerse > 1) {
                setCurrentVerse(currentVerse - 1);
            }
        };
        
        const handleNextVerse = () => {
            setCurrentVerse(currentVerse + 1);
        };
        
        // Keyboard navigation
        useEffect(() => {
            const handleKeyDown = (e) => {
                if (e.key === 'Escape') {
                    onClose();
                } else if (e.key === 'ArrowLeft') {
                    handlePrevVerse();
                } else if (e.key === 'ArrowRight') {
                    handleNextVerse();
                }
            };
            
            window.addEventListener('keydown', handleKeyDown);
            return () => window.removeEventListener('keydown', handleKeyDown);
        }, [currentVerse, onClose]);
        
        if (!book) return null;
        
        const fullBookName = getFullBookName(book);
        const reference = formatVerseReference(fullBookName, chapter, currentVerse, endVerse);
        
        // Render using createElement
        return React.createElement('div', { className: 'verse-preview-panel' },
            // Header
            React.createElement('div', { className: 'verse-preview-header' },
                React.createElement('h4', null, reference),
                React.createElement('button', {
                    className: 'verse-preview-close',
                    onClick: onClose
                }, '×')
            ),
            
            // Content
            React.createElement('div', { className: 'verse-preview-content' },
                loading && React.createElement('div', { className: 'loading' }, 'Loading...'),
                error && React.createElement('div', { className: 'error' }, error),
                data && !loading && React.createElement('div', { className: 'verse-context' },
                    data.verses.map((v) => 
                        React.createElement('div', {
                            key: v.verse,
                            className: `context-verse ${isVerseInRange(v.verse, currentVerse, endVerse) ? 'highlighted' : ''}`
                        },
                            React.createElement('span', { className: 'verse-num' }, v.verse),
                            React.createElement('span', { className: 'verse-text' }, v.text)
                        )
                    )
                )
            ),
            
            // Footer
            React.createElement('div', { className: 'verse-preview-footer' },
                React.createElement('button', {
                    className: 'btn-secondary',
                    onClick: handlePrevVerse,
                    disabled: currentVerse <= 1
                }, '← Prev'),
                React.createElement('button', {
                    className: 'btn-secondary',
                    onClick: handleNextVerse
                }, 'Next →'),
                React.createElement('button', {
                    className: 'btn-primary',
                    onClick: () => onReadChapter && onReadChapter(book, chapter, currentVerse)
                }, 'Read Chapter')
            )
        );
    };
}

export default createVersePanel;
