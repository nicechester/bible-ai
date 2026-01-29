/**
 * PreviewPane component for displaying markdown preview.
 * Note: This is designed to be used with React loaded from CDN.
 */

import { createMarkdownRenderer } from './MarkdownRenderer.js';

/**
 * Create the PreviewPane React component.
 * @param {Object} React - React library
 * @returns {Function} - React component
 */
export function createPreviewPane(React) {
    const MarkdownRenderer = createMarkdownRenderer(React);
    
    return function PreviewPane({ data, onVerseClick }) {
        return React.createElement('div', { className: 'preview-window' },
            // Header
            React.createElement('div', { className: 'preview-header' }, 'Preview'),
            
            // Content
            React.createElement('div', { className: 'preview-content' },
                // Error
                data?.error && React.createElement('div', { className: 'error' }, data.error),
                
                // Summary/Content
                data?.summary && React.createElement('div', { className: 'summary-section' },
                    React.createElement(MarkdownRenderer, {
                        content: data.summary,
                        onVerseClick: onVerseClick
                    })
                ),
                
                // No data placeholder
                !data && React.createElement('div', { className: 'loading' },
                    'No preview available. Start a conversation to see results.'
                )
            )
        );
    };
}

export default createPreviewPane;
