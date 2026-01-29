/**
 * Book name mappings for Korean and English Bible books.
 */

// Korean book name to short code
export const BOOK_NAMES_KR = {
    // Old Testament
    '창세기': '창', '출애굽기': '출', '레위기': '레', '민수기': '민', '신명기': '신',
    '여호수아': '수', '사사기': '삿', '룻기': '룻', '사무엘상': '삼상', '사무엘하': '삼하',
    '열왕기상': '왕상', '열왕기하': '왕하', '역대상': '대상', '역대하': '대하',
    '에스라': '스', '느헤미야': '느', '에스더': '에', '욥기': '욥',
    '시편': '시', '잠언': '잠', '전도서': '전', '아가': '아',
    '이사야': '사', '예레미야': '렘', '예레미아': '렘', '애가': '애',
    '에스겔': '겔', '다니엘': '단', '호세아': '호', '요엘': '욜',
    '아모스': '암', '오바댜': '옵', '요나': '욘', '미가': '미',
    '나훔': '나', '하박국': '합', '스바냐': '습', '학개': '학',
    '스가랴': '슥', '말라기': '말',
    
    // New Testament
    '마태복음': '마', '마가복음': '막', '누가복음': '눅', '요한복음': '요',
    '사도행전': '행', '로마서': '롬', '고린도전서': '고전', '고린도후서': '고후',
    '갈라디아서': '갈', '에베소서': '엡', '빌립보서': '빌', '골로새서': '골',
    '데살로니가전서': '살전', '데살로니가후서': '살후',
    '디모데전서': '딤전', '디모데후서': '딤후', '디도서': '딛',
    '빌레몬서': '몬', '히브리서': '히', '야고보서': '약',
    '베드로전서': '벧전', '베드로후서': '벧후',
    '요한일서': '요일', '요한이서': '요이', '요한삼서': '요삼',
    '유다서': '유', '요한계시록': '계', '계시록': '계'
};

// Short code to full Korean name
export const BOOK_FULL_NAMES_KR = Object.fromEntries(
    Object.entries(BOOK_NAMES_KR).map(([full, short]) => [short, full])
);

// English book name to short code
export const BOOK_NAMES_EN = {
    'genesis': 'Gen', 'exodus': 'Ex', 'leviticus': 'Lev',
    'numbers': 'Num', 'deuteronomy': 'Deut', 'joshua': 'Josh',
    'judges': 'Judg', 'ruth': 'Ruth', '1 samuel': '1Sam',
    '2 samuel': '2Sam', '1 kings': '1Kgs', '2 kings': '2Kgs',
    '1 chronicles': '1Chr', '2 chronicles': '2Chr', 'ezra': 'Ezra',
    'nehemiah': 'Neh', 'esther': 'Esth', 'job': 'Job',
    'psalms': 'Ps', 'psalm': 'Ps', 'proverbs': 'Prov', 'ecclesiastes': 'Eccl',
    'song of solomon': 'Song', 'isaiah': 'Isa', 'jeremiah': 'Jer',
    'lamentations': 'Lam', 'ezekiel': 'Ezek', 'daniel': 'Dan',
    'hosea': 'Hos', 'joel': 'Joel', 'amos': 'Amos',
    'obadiah': 'Obad', 'jonah': 'Jonah', 'micah': 'Mic',
    'nahum': 'Nah', 'habakkuk': 'Hab', 'zephaniah': 'Zeph',
    'haggai': 'Hag', 'zechariah': 'Zech', 'malachi': 'Mal',
    'matthew': 'Matt', 'mark': 'Mark', 'luke': 'Luke',
    'john': 'John', 'acts': 'Acts', 'romans': 'Rom',
    '1 corinthians': '1Cor', '2 corinthians': '2Cor',
    'galatians': 'Gal', 'ephesians': 'Eph', 'philippians': 'Phil',
    'colossians': 'Col', '1 thessalonians': '1Thess',
    '2 thessalonians': '2Thess', '1 timothy': '1Tim',
    '2 timothy': '2Tim', 'titus': 'Titus', 'philemon': 'Philem',
    'hebrews': 'Heb', 'james': 'Jas', '1 peter': '1Pet',
    '2 peter': '2Pet', '1 john': '1John', '2 john': '2John',
    '3 john': '3John', 'jude': 'Jude', 'revelation': 'Rev'
};

// Combined mappings for quick lookup
export const ALL_BOOK_MAPPINGS = { ...BOOK_NAMES_KR, ...BOOK_NAMES_EN };

/**
 * Normalize a book name to its short code.
 * @param {string} bookName - Full or partial book name
 * @returns {string} - Short book code or original if not found
 */
export function normalizeBookName(bookName) {
    if (!bookName) return bookName;
    
    const lower = bookName.toLowerCase().trim();
    
    // Check Korean names first
    if (BOOK_NAMES_KR[bookName]) {
        return BOOK_NAMES_KR[bookName];
    }
    
    // Check English names
    if (BOOK_NAMES_EN[lower]) {
        return BOOK_NAMES_EN[lower];
    }
    
    // Check if it's already a short code
    if (Object.values(BOOK_NAMES_KR).includes(bookName)) {
        return bookName;
    }
    
    return bookName;
}

/**
 * Get full book name from short code.
 * @param {string} shortCode - Book short code
 * @returns {string} - Full book name or original if not found
 */
export function getFullBookName(shortCode) {
    return BOOK_FULL_NAMES_KR[shortCode] || shortCode;
}
