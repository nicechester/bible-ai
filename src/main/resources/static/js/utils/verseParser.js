/**
 * Verse reference parsing and linkification utilities.
 */

import { BOOK_NAMES_KR, BOOK_NAMES_EN, normalizeBookName } from './bookMappings.js';

// Verse reference patterns (order matters - more specific patterns first)
const VERSE_PATTERNS = [
    // Korean with verse: 마태복음 8:5, 요한복음 3장 16절, 창 1:1-10
    {
        regex: /([가-힣]+(?:전서|후서|일서|이서|삼서)?)\s*(\d+)\s*[:장]\s*(\d+)(?:\s*[-~]\s*(\d+))?(?:절)?/g,
        parse: (match) => ({
            book: match[1],
            chapter: parseInt(match[2]),
            startVerse: parseInt(match[3]),
            endVerse: match[4] ? parseInt(match[4]) : null,
            chapterOnly: false,
            original: match[0]
        })
    },
    // Korean chapter-only: 민수기 31장, 창세기 1장
    {
        regex: /([가-힣]+(?:전서|후서|일서|이서|삼서)?)\s*(\d+)장(?!\s*\d)/g,
        parse: (match) => ({
            book: match[1],
            chapter: parseInt(match[2]),
            startVerse: 1,
            endVerse: null,
            chapterOnly: true,
            original: match[0]
        })
    },
    // English with verse: Matthew 8:5, John 3:16, Gen 1:1-10
    {
        regex: /(\d?\s*[A-Za-z]+)\s+(\d+):(\d+)(?:\s*-\s*(\d+))?/g,
        parse: (match) => ({
            book: match[1].trim(),
            chapter: parseInt(match[2]),
            startVerse: parseInt(match[3]),
            endVerse: match[4] ? parseInt(match[4]) : null,
            chapterOnly: false,
            original: match[0]
        })
    },
    // English chapter-only: Numbers 31, Genesis 1 (avoid matching mid-sentence numbers)
    {
        regex: /\b([1-3]?\s*[A-Z][a-z]+)\s+(\d+)(?!\s*:|\d)/g,
        parse: (match) => ({
            book: match[1].trim(),
            chapter: parseInt(match[2]),
            startVerse: 1,
            endVerse: null,
            chapterOnly: true,
            original: match[0]
        })
    }
];

/**
 * Parse all verse references from text.
 * @param {string} text - Text containing verse references
 * @returns {Array<Object>} - Array of parsed verse references
 */
export function parseVerseReferences(text) {
    const references = [];
    
    VERSE_PATTERNS.forEach(({ regex, parse }) => {
        let match;
        const pattern = new RegExp(regex.source, regex.flags);
        
        while ((match = pattern.exec(text)) !== null) {
            references.push(parse(match));
        }
    });
    
    return references;
}

/**
 * Convert text with verse references to HTML with clickable links.
 * @param {string} html - HTML content with verse references
 * @returns {string} - HTML with verse links
 */
export function linkifyVerseReferences(html) {
    let result = html;
    
    VERSE_PATTERNS.forEach(({ regex, parse }) => {
        const pattern = new RegExp(regex.source, regex.flags);
        result = result.replace(pattern, (match, ...groups) => {
            // Use the parse function to get structured data
            const parsed = parse([match, ...groups]);
            const bookShort = normalizeBookName(parsed.book);
            
            const dataBook = `data-book="${bookShort}"`;
            const dataChapter = `data-chapter="${parsed.chapter}"`;
            const dataStart = `data-start="${parsed.startVerse}"`;
            const dataEnd = parsed.endVerse ? ` data-end="${parsed.endVerse}"` : '';
            const dataChapterOnly = parsed.chapterOnly ? ' data-chapter-only="true"' : '';
            
            return `<a href="#" class="verse-link" ${dataBook} ${dataChapter} ${dataStart}${dataEnd}${dataChapterOnly}>${match}</a>`;
        });
    });
    
    return result;
}

/**
 * Parse a single verse reference string.
 * @param {string} refString - Reference string like "창세기 1:1" or "John 3:16"
 * @returns {Object|null} - Parsed reference or null if invalid
 */
export function parseVerseReference(refString) {
    const refs = parseVerseReferences(refString);
    return refs.length > 0 ? refs[0] : null;
}

/**
 * Format a verse reference for display.
 * @param {string} book - Book name or code
 * @param {number} chapter - Chapter number
 * @param {number} startVerse - Start verse number
 * @param {number} endVerse - End verse number (optional)
 * @returns {string} - Formatted reference string
 */
export function formatVerseReference(book, chapter, startVerse, endVerse = null) {
    const ref = `${book} ${chapter}:${startVerse}`;
    return endVerse && endVerse !== startVerse ? `${ref}-${endVerse}` : ref;
}

/**
 * Check if a verse number is in a range.
 * @param {number} verseNum - Verse number to check
 * @param {number} startVerse - Start of range
 * @param {number} endVerse - End of range (optional)
 * @returns {boolean} - True if in range
 */
export function isVerseInRange(verseNum, startVerse, endVerse = null) {
    if (endVerse) {
        return verseNum >= startVerse && verseNum <= endVerse;
    }
    return verseNum === startVerse;
}
